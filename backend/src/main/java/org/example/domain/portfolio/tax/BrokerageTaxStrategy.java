package org.example.domain.portfolio.tax;

import org.springframework.stereotype.Component;

/**
 * Обычный брокерский счёт: прибыль и купоны облагаются; действует ЛДВ (ст. 219.1) —
 * лот, удержанный ≥3 лет, освобождается на прибыль до 3 млн ₽ × полных лет владения.
 */
@Component
public class BrokerageTaxStrategy extends AbstractTaxStrategy {

    private static final long LDV_MIN_YEARS = 3;
    private static final double LDV_LIMIT_PER_YEAR = 3_000_000.0;

    @Override
    protected boolean gainTaxable() {
        return true;
    }

    @Override
    protected boolean couponTaxable() {
        return true;
    }

    @Override
    protected double ldvExemptGain(TaxContext ctx) {
        return ctx.sales().stream()
                .filter(s -> s.yearsHeld() >= LDV_MIN_YEARS && s.pnl() > 0)
                .mapToDouble(s -> Math.min(s.pnl(), LDV_LIMIT_PER_YEAR * s.yearsHeld()))
                .sum();
    }

    @Override
    protected String notes() {
        return "Брокерский счёт: НДФЛ на прибыль и купоны, ЛДВ при владении ≥3 лет.";
    }
}
