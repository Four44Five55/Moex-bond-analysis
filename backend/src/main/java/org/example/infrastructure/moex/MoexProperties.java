package org.example.infrastructure.moex;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки интеграции с MOEX ISS (префикс moex.* в application.yml). */
@ConfigurationProperties(prefix = "moex")
public record MoexProperties(
        String baseUrl,
        Http http,
        Sync sync
) {
    public record Http(int connectTimeoutMs, int readTimeoutMs, int maxRetries) {
    }

    public record Sync(boolean enabled, String cron) {
    }
}
