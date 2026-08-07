package org.example.domain.portfolio.tax;

import java.math.BigDecimal;

/**
 * Разбивка налога (НДФЛ). Все суммы в ₽, округлены до копейки;
 * отрицательный {@code realizedPnl} означает убыток.
 */
public record TaxBreakdown(
        BigDecimal realizedPnl,
        BigDecimal ldvExemptGain,
        BigDecimal taxableGain,
        BigDecimal gainTax,
        BigDecimal couponIncome,
        BigDecimal couponTax,
        BigDecimal dividendIncome,
        BigDecimal dividendTax,
        BigDecimal totalTax,
        String notes
) {
}
