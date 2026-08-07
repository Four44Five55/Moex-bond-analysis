package org.example.domain.portfolio.tax;

import org.example.domain.money.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Обычный брокерский счёт: прибыль и купоны облагаются; действует ЛДВ (ст. 219.1) —
 * лот, удержанный ≥3 лет, освобождается на прибыль до 3 млн ₽ × полных лет владения.
 */
@Component
public class BrokerageTaxStrategy extends AbstractTaxStrategy {

    private static final long LDV_MIN_YEARS = 3;
    private static final BigDecimal LDV_LIMIT_PER_YEAR = Money.of("3000000.00");

    @Override
    protected boolean gainTaxable() {
        return true;
    }

    @Override
    protected boolean couponTaxable() {
        return true;
    }

    @Override
    protected BigDecimal ldvExemptGain(TaxContext ctx) {
        return ctx.sales().stream()
                .filter(s -> s.yearsHeld() >= LDV_MIN_YEARS && Money.isPositive(s.pnl()))
                .map(s -> Money.min(s.pnl(), LDV_LIMIT_PER_YEAR.multiply(Money.of(s.yearsHeld()))))
                .reduce(Money.ZERO, BigDecimal::add);
    }

    @Override
    protected String notes() {
        return "Брокерский счёт: НДФЛ на прибыль и купоны, ЛДВ при владении ≥3 лет.";
    }
}
