package org.example.web.dto;

/**
 * Доходности инструмента за периоды (%) и 52-недельный диапазон цен для карточки.
 * Поля-изменения и диапазон могут быть null при недостатке истории.
 */
public record PerformanceDto(
        double lastPrice,
        Double change1d,
        Double change1w,
        Double change1m,
        Double change1y,
        Double changeYtd,
        Double high52w,
        Double low52w
) {
}
