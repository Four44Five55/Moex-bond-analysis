package org.example.infrastructure.moex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.domain.analysis.ReliabilityScorer;
import org.example.domain.model.Bond;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Выбор режима торгов при дедупликации. MOEX отдаёт одну бумагу на нескольких боардах,
 * и цены есть не на каждом: у части ОФЗ строка SPOB идёт первой и пуста целиком.
 */
class MoexBondParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MoexBondParser parser = new MoexBondParser(new ReliabilityScorer());

    /** Ответ ISS: columns + data, как отдаёт MOEX. Пустая строка — это отсутствующее значение. */
    private JsonNode response(String... rows) {
        String data = String.join(",", rows);
        return read("""
                {"securities":{"columns":["SECID","SHORTNAME","SECNAME","ISIN","CURRENCYID",
                  "BOARDID","FACEVALUE","PREVWAPRICE","PREVLEGALCLOSEPRICE","PREVPRICE",
                  "COUPONPERCENT","YIELDATPREVWAPRICE","ISSUESIZEPLACED","COUPONPERIOD",
                  "MATDATE","NEXTCOUPON","PREVDATE","ACCRUEDINT","LISTLEVEL"],
                 "data":[%s]}}
                """.formatted(data));
    }

    private String row(String board, String wa, String legalClose, String prev) {
        return """
                ["SU26248RMFS3","ОФЗ 26248","ОФЗ-ПД 26248","RU000A108EH3","SUR",
                 "%s",1000,%s,%s,%s,12.25,14.1,500000,182,
                 "2040-05-16","2026-11-18","2026-07-24",18.46,1]
                """.formatted(board, wa, legalClose, prev);
    }

    private JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void picksTheBoardThatActuallyHasAPrice() {
        // Пустой SPOB идёт первым — именно в таком порядке MOEX и отдаёт эти бумаги
        List<Bond> bonds = parser.parse(response(
                row("SPOB", "null", "null", "null"),
                row("TQOB", "82.447", "83.76", "83.66")));

        assertThat(bonds).singleElement().satisfies(b -> {
            assertThat(b.getBoard()).isEqualTo("TQOB");
            assertThat(b.getLastPrice()).isCloseTo(82.447, within(0.001));
        });
    }

    @Test
    void boardOrderDoesNotMatter() {
        List<Bond> bonds = parser.parse(response(
                row("TQOB", "82.447", "83.76", "83.66"),
                row("SPOB", "null", "null", "null")));

        assertThat(bonds).singleElement()
                .satisfies(b -> assertThat(b.getBoard()).isEqualTo("TQOB"));
    }

    @Test
    void fallsBackToClosingPriceWhenThereWereNoTrades() {
        // Неликвидная бумага: средневзвешенной нет, но цена закрытия есть.
        // Без отката такие облигации оценивались бы в ноль.
        List<Bond> bonds = parser.parse(response(row("TQCB", "null", "97.5", "97.4")));

        assertThat(bonds).singleElement()
                .satisfies(b -> assertThat(b.getLastPrice()).isCloseTo(97.5, within(0.001)));
    }

    @Test
    void keepsZeroPriceWhenNoBoardHasAny() {
        // Цены нет нигде — врать нечем, оставляем ноль и не роняем разбор
        List<Bond> bonds = parser.parse(response(row("SPOB", "null", "null", "null")));

        assertThat(bonds).singleElement()
                .satisfies(b -> assertThat(b.getLastPrice()).isZero());
    }
}
