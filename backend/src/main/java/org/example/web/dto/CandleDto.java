package org.example.web.dto;

/**
 * Свеча для графика. {@code time} — UNIX-время (сек) начала свечи (формат lightweight-charts).
 */
public record CandleDto(
        long time,
        double open,
        double high,
        double low,
        double close,
        double volume
) {
}
