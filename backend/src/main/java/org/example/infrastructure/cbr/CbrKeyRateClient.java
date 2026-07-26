package org.example.infrastructure.cbr;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.example.domain.port.KeyRateProvider;
import org.example.infrastructure.http.CompositeTrust;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Ключевая ставка из веб-сервиса ЦБ (SOAP, метод {@code KeyRate}).
 * <p>
 * Ряд ставок приходит <b>без выходных и праздников</b>: записи ровно за 1 января или 1 мая
 * в нём может не быть. Поэтому ставка «на 1-е число» берётся как последняя известная
 * на дату не позже первого числа — если брать запись строго за эту дату, половина месяцев
 * молча выпадет из максимума и вычет по вкладам окажется занижен.
 * <p>
 * По той же причине запрос начинается с декабря предыдущего года: ставку на 1 января
 * задаёт последнее декабрьское значение.
 */
@Component
public class CbrKeyRateClient implements KeyRateProvider {

    private static final Logger log = LoggerFactory.getLogger(CbrKeyRateClient.class);
    private static final MediaType SOAP_XML = MediaType.get("text/xml; charset=utf-8");
    private static final String SOAP_ACTION = "http://web.cbr.ru/KeyRate";

    private static final String ENVELOPE = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <KeyRate xmlns="http://web.cbr.ru/">
                  <fromDate>%s</fromDate>
                  <ToDate>%s</ToDate>
                </KeyRate>
              </soap:Body>
            </soap:Envelope>
            """;

    private final OkHttpClient client;
    private final String serviceUrl;

    public CbrKeyRateClient(@Value("${cbr.service-url}") String serviceUrl,
                            @Value("${cbr.timeout-ms}") long timeoutMs) {
        this.serviceUrl = serviceUrl;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        CompositeTrust.apply(builder);   // доверять JDK + Windows-ROOT (корпоративный TLS-прокси)
        this.client = builder.build();
    }

    @Override
    @Cacheable(cacheNames = "keyRate", key = "#year")
    public double maxKeyRateForYear(int year) {
        TreeMap<LocalDate, Double> series = fetch(
                LocalDate.of(year - 1, 12, 1), LocalDate.of(year, 12, 31));
        if (series.isEmpty()) {
            throw new CbrClientException("ЦБ не вернул ставок за " + year + " год");
        }

        LocalDate today = LocalDate.now();
        int lastMonth = today.getYear() == year ? today.getMonthValue() : 12;

        double max = 0;
        for (int month = 1; month <= lastMonth; month++) {
            Map.Entry<LocalDate, Double> entry = series.floorEntry(LocalDate.of(year, month, 1));
            if (entry != null) {
                max = Math.max(max, entry.getValue());
            }
        }
        if (max <= 0) {
            throw new CbrClientException("Не удалось определить ключевую ставку за " + year + " год");
        }
        log.info("Максимальная ключевая ставка за {} год: {}%", year, max);
        return max;
    }

    private TreeMap<LocalDate, Double> fetch(LocalDate from, LocalDate to) {
        Request request = new Request.Builder()
                .url(serviceUrl)
                .header("SOAPAction", SOAP_ACTION)
                .post(RequestBody.create(ENVELOPE.formatted(from, to), SOAP_XML))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new CbrClientException("ЦБ вернул HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new CbrClientException("Пустой ответ ЦБ");
            }
            return parse(body.bytes());
        } catch (IOException e) {
            throw new CbrClientException("Не удалось получить ключевую ставку из ЦБ", e);
        }
    }

    /** Разбор {@code <KR><DT>дата</DT><Rate>ставка</Rate></KR>} из SOAP-ответа. */
    TreeMap<LocalDate, Double> parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Ответ приходит по сети: внешние сущности в нём читать нельзя (XXE)
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            NodeList rows = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml))
                    .getElementsByTagName("KR");

            TreeMap<LocalDate, Double> series = new TreeMap<>();
            for (int i = 0; i < rows.getLength(); i++) {
                Element row = (Element) rows.item(i);
                String date = text(row, "DT");
                String rate = text(row, "Rate");
                if (date != null && rate != null) {
                    series.put(OffsetDateTime.parse(date).toLocalDate(),
                            Double.parseDouble(rate.trim()));
                }
            }
            return series;
        } catch (Exception e) {
            // Обрезаем по длине строки, а не массива: в UTF-8 кириллица занимает больше байт,
            // чем символов, и обрезание по xml.length выбрасывает StringIndexOutOfBounds
            String text = new String(xml, StandardCharsets.UTF_8);
            throw new CbrClientException("Не удалось разобрать ответ ЦБ: "
                    + text.substring(0, Math.min(200, text.length())), e);
        }
    }

    private String text(Element row, String tag) {
        NodeList nodes = row.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }
}
