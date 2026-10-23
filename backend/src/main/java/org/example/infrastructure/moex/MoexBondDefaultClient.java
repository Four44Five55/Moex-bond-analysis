package org.example.infrastructure.moex;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.domain.model.BondDefaultFlags;
import org.example.domain.port.BondDefaultProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Признаки дефолта из описания бумаги MOEX ({@code HASDEFAULT}, {@code HASTECHNICALDEFAULT}).
 * <p>
 * В общем списке торгов этих полей нет, поэтому синхронизация видит дефолт только по режиму
 * TQRD. Описание нужно для остального: технического дефолта и выпусков, которые уже погашены
 * (точнее, не погашены) и из списка торгов исчезли — у МОНОП 1P04 после даты погашения
 * справочник так и остался со старыми данными.
 */
@Component
public class MoexBondDefaultClient implements BondDefaultProvider {

    private final MoexHttpClient http;

    public MoexBondDefaultClient(MoexHttpClient http) {
        this.http = http;
    }

    @Override
    @Cacheable(cacheNames = "bondDefault", key = "#secId")
    public BondDefaultFlags flags(String secId) {
        JsonNode root = http.getJson("/securities/" + secId + ".json?iss.meta=off&iss.only=description");
        MoexTable table = MoexTable.of(root, "description");
        if (table == null) {
            return BondDefaultFlags.NONE;
        }
        boolean hasDefault = false;
        boolean hasTechnical = false;
        for (MoexTable.Row row : table) {
            String name = row.getString("name");
            boolean set = "1".equals(row.getString("value"));
            if ("HASDEFAULT".equals(name)) {
                hasDefault = set;
            } else if ("HASTECHNICALDEFAULT".equals(name)) {
                hasTechnical = set;
            }
        }
        return new BondDefaultFlags(hasDefault, hasTechnical);
    }
}
