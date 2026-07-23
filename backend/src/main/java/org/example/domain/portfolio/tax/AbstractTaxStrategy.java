package org.example.domain.portfolio.tax;

/**
 * Общий каркас расчёта НДФЛ (Template Method). Подклассы задают: облагается ли прибыль/купоны
 * и размер льготы ЛДВ. Прогрессия: базовая ставка до 5 млн ₽ базы, 15% сверх. Дивиденды — 13%.
 */
public abstract class AbstractTaxStrategy implements TaxStrategy {

    protected static final double DIVIDEND_RATE = 13.0;
    protected static final double PROGRESSIVE_THRESHOLD = 5_000_000.0;
    protected static final double HIGH_RATE = 15.0;

    protected abstract boolean gainTaxable();

    protected abstract boolean couponTaxable();

    protected abstract double ldvExemptGain(TaxContext ctx);

    protected abstract String notes();

    @Override
    public TaxBreakdown compute(TaxContext ctx) {
        double rate = ctx.account().getBaseTaxRate();

        double realizedPnl = ctx.sales().stream().mapToDouble(s -> s.pnl()).sum();
        double ldvExempt = gainTaxable() ? ldvExemptGain(ctx) : 0.0;
        double taxableGain = gainTaxable() ? Math.max(0.0, realizedPnl - ldvExempt) : 0.0;
        double gainTax = progressiveTax(taxableGain, rate);

        double couponTax = couponTaxable() ? ctx.couponIncome() * rate / 100.0 : 0.0;
        double dividendTax = ctx.dividendIncome() * DIVIDEND_RATE / 100.0;

        double total = gainTax + couponTax + dividendTax;

        return new TaxBreakdown(
                realizedPnl, ldvExempt, taxableGain, gainTax,
                ctx.couponIncome(), couponTax,
                ctx.dividendIncome(), dividendTax,
                total, notes());
    }

    protected double progressiveTax(double base, double baseRate) {
        if (base <= 0) {
            return 0.0;
        }
        if (base <= PROGRESSIVE_THRESHOLD) {
            return base * baseRate / 100.0;
        }
        return PROGRESSIVE_THRESHOLD * baseRate / 100.0
                + (base - PROGRESSIVE_THRESHOLD) * HIGH_RATE / 100.0;
    }
}
