package org.example.infrastructure.moex;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.domain.model.Candle;
import org.example.domain.port.CandleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Адаптер MOEX candles → доменные {@link Candle}.
 * Эндпоинт: /engines/stock/markets/{market}/securities/{secid}/candles.json
 */
@Component
public class MoexCandleClient implements CandleProvider {
    private static final Logger logger = LoggerFactory.getLogger(MoexCandleClient.class);
    private static final DateTimeFormatter MOEX_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MoexHttpClient http;

    public MoexCandleClient(MoexHttpClient http) {
        this.http = http;
    }

    @Override
    public List<Candle> fetchCandles(String secId, String market, int intervalCode, LocalDate from, LocalDate to) {
        String path = String.format(
                "/engines/stock/markets/%s/securities/%s/candles.json?interval=%d&from=%s&till=%s&iss.meta=off",
                market, secId, intervalCode, from, to);
        JsonNode root = http.getJson(path);

        MoexTable table = MoexTable.of(root, "candles");
        if (table == null) {
            return List.of();
        }

        List<Candle> candles = new ArrayList<>();
        for (MoexTable.Row row : table) {
            Candle candle = toCandle(row, secId, market, intervalCode);
            if (candle != null) {
                candles.add(candle);
            }
        }
        logger.debug("Загружено {} свечей для {} ({})", candles.size(), secId, market);
        return candles;
    }

    private Candle toCandle(MoexTable.Row row, String secId, String market, int intervalCode) {
        Instant ts = parseTs(row.getString("begin"));
        if (ts == null) {
            return null;
        }
        Candle candle = new Candle();
        candle.setSecId(secId);
        candle.setMarket(market);
        candle.setIntervalCode(intervalCode);
        candle.setTs(ts);
        candle.setOpen(row.getDouble("open"));
        candle.setHigh(row.getDouble("high"));
        candle.setLow(row.getDouble("low"));
        candle.setClose(row.getDouble("close"));
        candle.setVolume(row.getDouble("volume"));
        return candle;
    }

    private Instant parseTs(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, MOEX_DT).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }
}
