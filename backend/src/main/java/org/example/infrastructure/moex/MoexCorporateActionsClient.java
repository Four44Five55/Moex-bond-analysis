package org.example.infrastructure.moex;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.domain.model.AmortizationPayment;
import org.example.domain.model.CouponPayment;
import org.example.domain.model.DividendPayment;
import org.example.domain.port.CorporateActionsProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Адаптер MOEX к порту {@link CorporateActionsProvider}.
 * Купоны/амортизации — из bondization.json, дивиденды — из dividends.json. Кешируется в Redis.
 */
@Component
public class MoexCorporateActionsClient implements CorporateActionsProvider {

    private final MoexHttpClient http;

    public MoexCorporateActionsClient(MoexHttpClient http) {
        this.http = http;
    }

    @Override
    @Cacheable(cacheNames = "coupons", key = "#secId")
    public List<CouponPayment> coupons(String secId) {
        JsonNode root = http.getJson(bondizationPath(secId));
        MoexTable table = MoexTable.of(root, "coupons");
        List<CouponPayment> result = new ArrayList<>();
        if (table == null) {
            return result;
        }
        for (MoexTable.Row row : table) {
            LocalDate date = row.getDate("coupondate");
            double value = row.getDouble("value");
            if (date != null && value > 0) {
                result.add(new CouponPayment(date, value));
            }
        }
        return result;
    }

    @Override
    @Cacheable(cacheNames = "amortizations", key = "#secId")
    public List<AmortizationPayment> amortizations(String secId) {
        JsonNode root = http.getJson(bondizationPath(secId));
        MoexTable table = MoexTable.of(root, "amortizations");
        List<AmortizationPayment> result = new ArrayList<>();
        if (table == null) {
            return result;
        }
        for (MoexTable.Row row : table) {
            LocalDate date = row.getDate("amortdate");
            double value = row.getDouble("value");
            if (date != null && value > 0) {
                result.add(new AmortizationPayment(date, value));
            }
        }
        return result;
    }

    @Override
    @Cacheable(cacheNames = "dividends", key = "#secId")
    public List<DividendPayment> dividends(String secId) {
        JsonNode root = http.getJson("/securities/" + secId + "/dividends.json?iss.meta=off");
        MoexTable table = MoexTable.of(root, "dividends");
        List<DividendPayment> result = new ArrayList<>();
        if (table == null) {
            return result;
        }
        for (MoexTable.Row row : table) {
            LocalDate date = row.getDate("registryclosedate");
            double value = row.getDouble("value");
            if (date != null && value > 0) {
                result.add(new DividendPayment(date, value, row.getString("currencyid")));
            }
        }
        return result;
    }

    private String bondizationPath(String secId) {
        return "/securities/" + secId + "/bondization.json?iss.meta=off&limit=unlimited";
    }
}
