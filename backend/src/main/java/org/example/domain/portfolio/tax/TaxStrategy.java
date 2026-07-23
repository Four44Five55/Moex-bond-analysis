package org.example.domain.portfolio.tax;

/** Стратегия расчёта налога, зависящая от типа счёта (Strategy). */
public interface TaxStrategy {

    TaxBreakdown compute(TaxContext context);
}
