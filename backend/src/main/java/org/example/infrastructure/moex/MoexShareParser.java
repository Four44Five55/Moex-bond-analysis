package org.example.infrastructure.moex;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.domain.model.Share;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Преобразует ответ MOEX по рынку акций в доменные {@link Share}.
 * Берётся основной режим торгов (TQBR); рыночные метрики — из блока marketdata.
 */
@Component
public class MoexShareParser {
    private static final Logger logger = LoggerFactory.getLogger(MoexShareParser.class);
    private static final String PRIMARY_BOARD = "TQBR";

    public List<Share> parse(JsonNode root) {
        MoexTable securities = MoexTable.of(root, "securities");
        if (securities == null) {
            throw new MoexClientException("MOEX: отсутствует блок securities для акций");
        }
        Map<String, MarketRow> marketBySecId = readMarketData(MoexTable.of(root, "marketdata"));

        Map<String, Share> bySecId = new LinkedHashMap<>();
        for (MoexTable.Row row : securities) {
            if (!PRIMARY_BOARD.equals(row.getString("BOARDID"))) {
                continue;
            }
            Share share = toShare(row, marketBySecId);
            if (share != null) {
                bySecId.putIfAbsent(share.getSecId(), share);
            }
        }
        logger.debug("Разобрано {} акций", bySecId.size());
        return List.copyOf(bySecId.values());
    }

    private Share toShare(MoexTable.Row row, Map<String, MarketRow> marketBySecId) {
        try {
            String secId = row.getString("SECID");
            if (secId.isEmpty()) {
                return null;
            }

            Share share = new Share();
            share.setSecId(secId);
            share.setShortName(row.getString("SHORTNAME"));
            share.setFullName(row.getString("SECNAME"));
            share.setIsin(row.getString("ISIN"));
            share.setCurrency(normalizeCurrency(row.getString("CURRENCYID")));
            share.setBoard(PRIMARY_BOARD);
            share.setMarket("shares");
            share.setLotSize(row.getInt("LOTSIZE"));
            share.setListLevel(row.getInt("LISTLEVEL"));
            share.setIssueSize(row.getDouble("ISSUESIZE"));
            share.setSector(emptyToNull(row.getString("SECTORID")));

            double prevPrice = row.getDouble("PREVPRICE");
            share.setPrevPrice(prevPrice);

            MarketRow md = marketBySecId.get(secId);
            if (md != null) {
                share.setLastPrice(md.last > 0 ? md.last : prevPrice);
                share.setDayChangePct(md.changePct);
                share.setTradeVolume(md.valToday);
                share.setCapitalization(md.capitalization);
            } else {
                share.setLastPrice(prevPrice);
            }
            share.setUpdatedAt(Instant.now());
            return share;
        } catch (Exception e) {
            logger.debug("Пропущена акция из-за ошибки разбора: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, MarketRow> readMarketData(MoexTable marketData) {
        Map<String, MarketRow> result = new HashMap<>();
        if (marketData == null) {
            return result;
        }
        for (MoexTable.Row row : marketData) {
            if (!PRIMARY_BOARD.equals(row.getString("BOARDID"))) {
                continue;
            }
            String secId = row.getString("SECID");
            if (!secId.isEmpty()) {
                result.putIfAbsent(secId, new MarketRow(
                        row.getDouble("LAST"),
                        row.getDouble("LASTCHANGEPRCNT"),
                        row.getDouble("VALTODAY"),
                        row.getDouble("ISSUECAPITALIZATION")
                ));
            }
        }
        return result;
    }

    private String normalizeCurrency(String moexCurrency) {
        return "SUR".equalsIgnoreCase(moexCurrency) ? "RUB" : moexCurrency;
    }

    private String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private record MarketRow(double last, double changePct, double valToday, double capitalization) {
    }
}
