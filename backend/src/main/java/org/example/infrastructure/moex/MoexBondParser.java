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
                bySecId.putIfAbsent(bond.getSecId(), bond);
            }
        }
        logger.debug("Разобрано {} уникальных облигаций", bySecId.size());
        return List.copyOf(bySecId.values());
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
            bond.setLastPrice(row.getDouble("PREVWAPRICE"));         // цена в % от номинала
            bond.setPrevPrice(row.getDouble("PREVLEGALCLOSEPRICE"));
            bond.setCouponRate(row.getDouble("COUPONPERCENT"));
            bond.setYieldToMaturity(row.getDouble("YIELDATPREVWAPRICE"));
            bond.setTradeVolume(row.getDouble("ISSUESIZEPLACED"));
            bond.setCouponFrequency(row.getInt("COUPONPERIOD"));

            bond.setMaturityDate(row.getDate("MATDATE"));
            bond.setNextCouponDate(row.getDate("NEXTCOUPON"));
            bond.setIssueDate(row.getDate("PREVDATE"));

            bond.setCallable(row.hasNonEmpty("CALLOPTIONDATE"));
            bond.setPuttable(row.hasNonEmpty("PUTOPTIONDATE"));

            Double durationDays = durationBySecId.get(bond.getSecId());
            if (durationDays != null) {
                bond.setDuration(durationDays / DAYS_PER_YEAR);
            }

            reliabilityScorer.score(bond, row.getInt("LISTLEVEL"));
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

    private boolean isSuspicious(double ytm) {
        return ytm > SUSPICIOUS_YIELD_PCT || ytm < 0;
    }

    private boolean isInvalid(Bond bond) {
        return bond.getSecId() == null || bond.getSecId().isEmpty()
                || bond.getFaceValue() <= 0
                || bond.getMaturityDate() == null;
    }
}
