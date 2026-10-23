package org.example.infrastructure.moex;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.domain.analysis.ReliabilityScorer;
import org.example.domain.model.Bond;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Преобразует ответ MOEX по рынку облигаций в доменные {@link Bond}.
 * Дюрация берётся из блока marketdata (в днях) и переводится в годы.
 */
@Component
public class MoexBondParser {
    private static final Logger logger = LoggerFactory.getLogger(MoexBondParser.class);
    private static final double DAYS_PER_YEAR = 365.0;
    /** Порог аномальной доходности (сырьё MOEX иногда содержит YTM в сотни %). */
    private static final double SUSPICIOUS_YIELD_PCT = 100.0;
    /** Режим «Т+: Облигации Д» — на нём MOEX торгует только выпусками в дефолте. */
    private static final String DEFAULT_BOARD = "TQRD";

    private final ReliabilityScorer reliabilityScorer;

    public MoexBondParser(ReliabilityScorer reliabilityScorer) {
        this.reliabilityScorer = reliabilityScorer;
    }

    public List<Bond> parse(JsonNode root) {
        MoexTable securities = MoexTable.of(root, "securities");
        if (securities == null) {
            throw new MoexClientException("MOEX: отсутствует блок securities для облигаций");
        }
        Map<String, Double> durationBySecId = readDurations(MoexTable.of(root, "marketdata"));

        // MOEX отдаёт одну бумагу на нескольких режимах торгов — дедуплицируем по SECID
        Map<String, Bond> bySecId = new LinkedHashMap<>();
        for (MoexTable.Row row : securities) {
            Bond bond = toBond(row, durationBySecId);
            if (bond != null) {
                bySecId.merge(bond.getSecId(), bond, MoexBondParser::preferPriced);
            }
        }
        logger.debug("Разобрано {} уникальных облигаций", bySecId.size());
        return List.copyOf(bySecId.values());
    }

    /**
     * Из двух режимов торгов по одной бумаге оставляем тот, где есть цена.
     * <p>
     * Раньше здесь стоял {@code putIfAbsent}, то есть побеждал режим, который MOEX отдал первым.
     * Для части ОФЗ первым идёт SPOB, где все ценовые поля пустые, — бумага сохранялась с ценой 0.
     * В портфеле такая позиция оценивалась в ноль и выглядела как стопроцентный убыток,
     * а в аллокации не получала доли вовсе.
     */
    private static Bond preferPriced(Bond existing, Bond candidate) {
        boolean existingPriced = existing.getLastPrice() != null && existing.getLastPrice() > 0;
        boolean candidatePriced = candidate.getLastPrice() != null && candidate.getLastPrice() > 0;
        Bond chosen = !existingPriced && candidatePriced ? candidate : existing;
        // Дефолт — свойство выпуска, а не режима: торгуйся бумага в TQRD хоть на одном из них
        if (existing.isHasDefault() || candidate.isHasDefault()) {
            markDefaulted(chosen);
        }
        return chosen;
    }

    /** Надёжность бумаги в дефолте — минимальная, сколь бы высок ни был уровень листинга. */
    private static void markDefaulted(Bond bond) {
        bond.setHasDefault(true);
        bond.setReliabilityScore(1);
        bond.setInvestmentGrade(false);
    }

    private Bond toBond(MoexTable.Row row, Map<String, Double> durationBySecId) {
        try {
            Bond bond = new Bond();
            bond.setSecId(row.getString("SECID"));
            bond.setShortName(row.getString("SHORTNAME"));
            bond.setFullName(row.getString("SECNAME"));
            bond.setIsin(row.getString("ISIN"));
            bond.setCurrency(row.getString("CURRENCYID"));
            bond.setBoard(row.getString("BOARDID"));
            bond.setMarket("bonds");

            bond.setFaceValue(row.getDouble("FACEVALUE"));
            // Цена в % от номинала. PREVWAPRICE — средневзвешенная прошлой сессии, и у бумаг
            // без сделок она пуста; тогда берём цену закрытия. Иначе сотни неликвидных
            // облигаций остались бы с нулевой ценой и нулевой оценкой в портфеле.
            bond.setLastPrice(firstPositive(row, "PREVWAPRICE", "PREVLEGALCLOSEPRICE", "PREVPRICE"));
            bond.setPrevPrice(row.getDouble("PREVLEGALCLOSEPRICE"));
            bond.setCouponRate(row.getDouble("COUPONPERCENT"));
            bond.setYieldToMaturity(row.getDouble("YIELDATPREVWAPRICE"));
            bond.setTradeVolume(row.getDouble("ISSUESIZEPLACED"));
            bond.setCouponFrequency(row.getInt("COUPONPERIOD"));

            bond.setMaturityDate(row.getDate("MATDATE"));
            bond.setNextCouponDate(row.getDate("NEXTCOUPON"));
            bond.setIssueDate(row.getDate("PREVDATE"));
            bond.setAccruedInterest(row.getDouble("ACCRUEDINT"));
            bond.setOfferDate(firstNonNullDate(row, "OFFERDATE", "BUYBACKDATE"));

            bond.setCallable(row.hasNonEmpty("CALLOPTIONDATE"));
            bond.setPuttable(row.hasNonEmpty("PUTOPTIONDATE"));

            Double durationDays = durationBySecId.get(bond.getSecId());
            if (durationDays != null) {
                bond.setDuration(durationDays / DAYS_PER_YEAR);
            }

            reliabilityScorer.score(bond, row.getInt("LISTLEVEL"));
            if (DEFAULT_BOARD.equals(bond.getBoard())) {
                markDefaulted(bond);
            }
            bond.setSuspiciousYield(isSuspicious(bond.getYieldToMaturity()));
            bond.setUpdatedAt(Instant.now());

            if (isInvalid(bond)) {
                return null;
            }
            return bond;
        } catch (Exception e) {
            logger.debug("Пропущена облигация из-за ошибки разбора: {}", e.getMessage());
            return null;
        }
    }

    /** Первое положительное значение из перечисленных столбцов; 0, если цены нет нигде. */
    private double firstPositive(MoexTable.Row row, String... columns) {
        for (String column : columns) {
            double value = row.getDouble(column);
            if (value > 0) {
                return value;
            }
        }
        return 0.0;
    }

    /** Первая непустая дата из перечисленных столбцов (у разных выпусков оферта в разных полях). */
    private java.time.LocalDate firstNonNullDate(MoexTable.Row row, String... columns) {
        for (String column : columns) {
            java.time.LocalDate date = row.getDate(column);
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private Map<String, Double> readDurations(MoexTable marketData) {
        Map<String, Double> result = new HashMap<>();
        if (marketData == null || !marketData.has("DURATION") || !marketData.has("SECID")) {
            logger.warn("Блок marketdata без DURATION — дюрация облигаций недоступна");
            return result;
        }
        for (MoexTable.Row row : marketData) {
            String secId = row.getString("SECID");
            double days = row.getDouble("DURATION");
            if (!secId.isEmpty() && days > 0) {
                result.put(secId, days);
            }
        }
        return result;
    }

    /** Неизвестная доходность подозрительной не считается: подозревать нечего. */
    private boolean isSuspicious(Double ytm) {
        return ytm != null && (ytm > SUSPICIOUS_YIELD_PCT || ytm < 0);
    }

    private boolean isInvalid(Bond bond) {
        return bond.getSecId() == null || bond.getSecId().isEmpty()
                || bond.getFaceValue() <= 0
                || bond.getMaturityDate() == null;
    }
}
