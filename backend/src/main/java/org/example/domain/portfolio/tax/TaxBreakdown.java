package org.example.domain.portfolio.tax;

/**
 * Разбивка налога (НДФЛ). Все суммы в ₽; отрицательный {@code realizedPnl} означает убыток.
 */
public record TaxBreakdown(
        double realizedPnl,
        double ldvExemptGain,
        double taxableGain,
        double gainTax,
        double couponIncome,
        double couponTax,
        double dividendIncome,
        double dividendTax,
        double totalTax,
        String notes
) {
}
