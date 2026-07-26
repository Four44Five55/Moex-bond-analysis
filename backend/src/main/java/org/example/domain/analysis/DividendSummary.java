package org.example.domain.analysis;

/**
 * Итог по дивидендам за скользящие 12 месяцев.
 * {@code trailingSum} и {@code yieldPct} — null, если выплат/цены нет.
 */
public record DividendSummary(Double trailingSum, Double yieldPct, int count) {

    public static DividendSummary empty() {
        return new DividendSummary(null, null, 0);
    }
}
