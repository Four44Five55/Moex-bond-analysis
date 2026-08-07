package org.example.domain.portfolio.tax;

import org.example.domain.model.Account;
import org.example.domain.portfolio.RealizedSale;

import java.math.BigDecimal;
import java.util.List;

/**
 * Входные данные для расчёта налога: счёт, реализованные продажи (для прибыли и ЛДВ)
 * и прогнозируемый годовой доход (купоны/дивиденды, gross).
 */
public record TaxContext(
        Account account,
        List<RealizedSale> sales,
        BigDecimal couponIncome,
        BigDecimal dividendIncome
) {
}
