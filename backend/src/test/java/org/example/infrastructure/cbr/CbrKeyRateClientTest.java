package org.example.infrastructure.cbr;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Разбор SOAP-ответа ЦБ. Фрагмент взят из живого ответа сервиса — включая обёртки
 * diffgram, из-за которых наивный поиск по корню документа ничего не находит.
 */
class CbrKeyRateClientTest {

    private static final String RESPONSE = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <KeyRateResponse xmlns="http://web.cbr.ru/">
                  <KeyRateResult>
                    <diffgr:diffgram xmlns:diffgr="urn:schemas-microsoft-com:xml-diffgram-v1">
                      <KeyRate xmlns="">
                        <KR><DT>2026-07-01T00:00:00+03:00</DT><Rate>14.25</Rate></KR>
                        <KR><DT>2026-04-01T00:00:00+03:00</DT><Rate>15.00</Rate></KR>
                        <KR><DT>2025-12-30T00:00:00+03:00</DT><Rate>16.00</Rate></KR>
                      </KeyRate>
                    </diffgr:diffgram>
                  </KeyRateResult>
                </KeyRateResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    private final CbrKeyRateClient client = new CbrKeyRateClient("http://localhost", 1000);

    @Test
    void parsesDatesAndRatesFromSoapEnvelope() {
        TreeMap<LocalDate, Double> series = client.parse(RESPONSE.getBytes(StandardCharsets.UTF_8));

        assertThat(series).hasSize(3);
        assertThat(series.get(LocalDate.of(2026, 7, 1))).isEqualTo(14.25);
        assertThat(series.get(LocalDate.of(2025, 12, 30))).isEqualTo(16.00);
    }

    @Test
    void seriesIsSortedSoRateOnAHolidayCanBeLookedUpBackwards() {
        TreeMap<LocalDate, Double> series = client.parse(RESPONSE.getBytes(StandardCharsets.UTF_8));

        // 1 января выходной, записи за него нет — ставку задаёт последнее декабрьское значение
        assertThat(series.floorEntry(LocalDate.of(2026, 1, 1)).getValue()).isEqualTo(16.00);
        // 1 мая тоже выходной — действует апрельская ставка
        assertThat(series.floorEntry(LocalDate.of(2026, 5, 1)).getValue()).isEqualTo(15.00);
    }

    @Test
    void garbageResponseIsReportedAsClientError() {
        assertThatThrownBy(() -> client.parse("не xml".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CbrClientException.class);
    }
}
