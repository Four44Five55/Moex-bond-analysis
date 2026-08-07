package org.example.domain.portfolio.tax;

import org.example.domain.money.Money;
import org.example.domain.portfolio.RealizedSale;

import java.math.BigDecimal;

/**
 * Общий каркас расчёта НДФЛ (Template Method). Подклассы задают: облагается ли прибыль/купоны
 * и размер льготы ЛДВ. Прогрессия: базовая ставка до 5 млн ₽ базы, 15% сверх. Дивиденды — 13%.
 * <p>
 * Суммы налога округляются до копейки на выходе: промежуточные умножения на ставку дают
 * длинный хвост, и без явного округления в отчёте появлялись бы суммы вида 1234.5678999 ₽.
 */
public abstract class AbstractTaxStrategy implements TaxStrategy {

    protected abstract boolean gainTaxable();

    protected abstract boolean couponTaxable();

    protected abstract BigDecimal ldvExemptGain(TaxContext ctx);

    protected abstract String notes();

    @Override
    public TaxBreakdown compute(TaxContext ctx) {
        BigDecimal rate = ctx.account().getBaseTaxRate();

        BigDecimal realizedPnl = ctx.sales().stream()
                .map(RealizedSale::pnl)
                .reduce(Money.ZERO, BigDecimal::add);
        BigDecimal ldvExempt = gainTaxable() ? ldvExemptGain(ctx) : Money.ZERO;
        BigDecimal taxableGain = gainTaxable()
                ? Money.atLeastZero(realizedPnl.subtract(ldvExempt))
                : Money.ZERO;
        BigDecimal gainTax = progressiveTax(taxableGain, rate);

        BigDecimal couponTax = couponTaxable()
                ? Money.pct(ctx.couponIncome(), rate)
                : Money.ZERO;
        BigDecimal dividendTax = Money.pct(ctx.dividendIncome(), TaxRates.DIVIDEND_PCT);

        BigDecimal total = gainTax.add(couponTax).add(dividendTax);

        return new TaxBreakdown(
                Money.round(realizedPnl), Money.round(ldvExempt),
                Money.round(taxableGain), Money.round(gainTax),
                Money.round(ctx.couponIncome()), Money.round(couponTax),
                Money.round(ctx.dividendIncome()), Money.round(dividendTax),
                Money.round(total), notes());
    }

    protected BigDecimal progressiveTax(BigDecimal base, BigDecimal baseRate) {
        if (!Money.isPositive(base)) {
            return Money.ZERO;
        }
        if (base.compareTo(TaxRates.PROGRESSIVE_THRESHOLD) <= 0) {
            return Money.pct(base, baseRate);
        }
        BigDecimal excess = base.subtract(TaxRates.PROGRESSIVE_THRESHOLD);
        return Money.pct(TaxRates.PROGRESSIVE_THRESHOLD, baseRate)
                .add(Money.pct(excess, TaxRates.HIGH_PCT));
    }
}
