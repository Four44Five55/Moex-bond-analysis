package org.example.domain.analysis;

/**
 * Доходности инструмента за периоды (в %) и 52-недельный диапазон цен.
 * Поля-изменения и диапазон — {@code null}, если истории недостаточно.
 */
public record PerformanceMetrics(
        double lastPrice,
        Double change1d,
        Double change1w,
        Double change1m,
        Double change1y,
        Double changeYtd,
        Double high52w,
        Double low52w
) {
    public static PerformanceMetrics empty() {
        return new PerformanceMetrics(0, null, null, null, null, null, null, null);
    }
}
