package org.example.infrastructure.moex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Транспортный адаптер к MOEX ISS: GET JSON с таймаутами и ретраями с экспоненциальным backoff.
 * Отвечает только за сетевой ввод-вывод (SRP); разбор — в парсерах.
 */
@Component
public class MoexHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(MoexHttpClient.class);

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final MoexProperties properties;

    public MoexHttpClient(MoexProperties properties) {
        this.properties = properties;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(properties.http().connectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.http().readTimeoutMs(), TimeUnit.MILLISECONDS);
        CompositeTrust.apply(builder);   // доверять JDK + Windows-ROOT (корпоративный TLS-прокси)
        this.client = builder.build();
    }

    /** Абсолютный или относительный (к base-url) путь → распарсенный JSON. */
    public JsonNode getJson(String pathOrUrl) {
        String url = pathOrUrl.startsWith("http") ? pathOrUrl : properties.baseUrl() + pathOrUrl;
        int attempts = Math.max(1, properties.http().maxRetries());
        IOException last = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code() + " для " + url);
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Пустой ответ MOEX для " + url);
                }
                return mapper.readTree(body.string());
            } catch (IOException e) {
                last = e;
                logger.warn("MOEX запрос не удался (попытка {}/{}): {}", attempt, attempts, e.getMessage());
                sleepBackoff(attempt);
            }
        }
        throw new MoexClientException("Не удалось получить данные MOEX: " + url, last);
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(Duration.ofMillis(200L * attempt).toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
