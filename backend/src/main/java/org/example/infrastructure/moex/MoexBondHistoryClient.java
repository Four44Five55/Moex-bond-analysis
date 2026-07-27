package org.example.infrastructure.moex;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.domain.model.AccruedInterest;
import org.example.domain.port.BondHistoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * История торгов облигации в MOEX ISS. Нужна ради колонки {@code ACCINT} — НКД на дату,
 * которого нет ни в справочнике (там только сегодняшний), ни в графике купонов.
 * <p>
 * Ответ кешируется по паре «бумага + дата»: значение за прошедший день уже не изменится,
 * а форма ввода сделки дёргает его при каждой правке даты.
 */
@Component
public class MoexBondHistoryClient implements BondHistoryProvider {

    private static final Logger log = LoggerFactory.getLogger(MoexBondHistoryClient.class);

    /** Окно поиска ближайшего торгового дня назад: новогодние каникулы длиннее недели. */
    private static final int LOOKBACK_DAYS = 14;

    private final MoexHttpClient http;

    public MoexBondHistoryClient(MoexHttpClient http) {
        this.http = http;
    }

    @Override
    @Cacheable(cacheNames = "bondAccruedInterest", key = "#secId + ':' + #date")
    public AccruedInterest accruedInterestOn(String secId, LocalDate date) {
        String path = "/history/engines/stock/markets/bonds/securities/" + secId + ".json"
                + "?iss.meta=off&iss.only=history&history.columns=TRADEDATE,ACCINT"
                + "&from=" + date.minusDays(LOOKBACK_DAYS) + "&till=" + date;

        JsonNode root = http.getJson(path);
        MoexTable table = MoexTable.of(root, "history");
        if (table == null) {
            return AccruedInterest.unknown();
        }

        // Строки приходят по возрастанию даты — нужна последняя с непустым НКД
        AccruedInterest last = AccruedInterest.unknown();
        for (MoexTable.Row row : table) {
            LocalDate tradeDate = row.getDate("TRADEDATE");
            Double accint = row.getDoubleOrNull("ACCINT");
            if (tradeDate != null && accint != null && !tradeDate.isAfter(date)) {
                last = new AccruedInterest(tradeDate, accint, true);
            }
        }
        if (!last.known()) {
            log.debug("НКД по {} на {} не найден за {} дней истории", secId, date, LOOKBACK_DAYS);
        }
        return last;
    }
}
