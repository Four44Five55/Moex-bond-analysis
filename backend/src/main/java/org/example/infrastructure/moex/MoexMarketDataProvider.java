package org.example.infrastructure.moex;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.domain.model.Bond;
import org.example.domain.model.Share;
import org.example.domain.port.MarketDataProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Адаптер MOEX ISS к порту {@link MarketDataProvider}: связывает транспорт
 * ({@link MoexHttpClient}) и парсеры конкретных рынков (Strategy).
 */
@Component
public class MoexMarketDataProvider implements MarketDataProvider {

    private static final String BONDS_PATH =
            "/engines/stock/markets/bonds/securities.json?iss.meta=off";
    private static final String SHARES_PATH =
            "/engines/stock/markets/shares/securities.json?iss.meta=off";

    private final MoexHttpClient http;
    private final MoexBondParser bondParser;
    private final MoexShareParser shareParser;

    public MoexMarketDataProvider(MoexHttpClient http, MoexBondParser bondParser, MoexShareParser shareParser) {
        this.http = http;
        this.bondParser = bondParser;
        this.shareParser = shareParser;
    }

    @Override
    public List<Bond> fetchBonds() {
        JsonNode root = http.getJson(BONDS_PATH);
        return bondParser.parse(root);
    }

    @Override
    public List<Share> fetchShares() {
        JsonNode root = http.getJson(SHARES_PATH);
        return shareParser.parse(root);
    }
}
