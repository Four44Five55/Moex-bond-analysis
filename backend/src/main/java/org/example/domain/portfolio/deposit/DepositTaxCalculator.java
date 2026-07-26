package org.example.domain.portfolio.deposit;

import org.example.domain.portfolio.tax.TaxRates;
import org.springframework.stereotype.Component;

/**
 * НДФЛ с процентов по вкладам. Считается <b>на уровне клиента, а не отдельного вклада</b>:
 * необлагаемый вычет (1 млн ₽ × максимальная ключевая ставка за год) даётся один раз на все
 * вклады сразу, поэтому посчитать налог по каждому вкладу и сложить — значит выдать вычет
 * столько раз, сколько у человека вкладов, и занизить налог в разы.
 * <p>
 * Этим вклады принципиально отличаются от купонов и дивидендов, где налог считает
 * {@code TaxStrategy} в разрезе счёта. Расчёт чистый: без БД и сети.
 */
@Component
public class DepositTaxCalculator {

    /**
     * @param interestIncome проценты, полученные за календарный год по всем вкладам клиента
     * @param keyRatePct     максимальная ключевая ставка ЦБ за этот год, %
     */
    public DepositTaxBreakdown compute(double interestIncome, double keyRatePct) {
        double deduction = TaxRates.DEPOSIT_DEDUCTION_BASE * keyRatePct / 100.0;
        double taxable = Math.max(0.0, interestIncome - deduction);
        double tax = taxable <= TaxRates.PROGRESSIVE_THRESHOLD
                ? taxable * TaxRates.BASE_PCT / 100.0
                : TaxRates.PROGRESSIVE_THRESHOLD * TaxRates.BASE_PCT / 100.0
                        + (taxable - TaxRates.PROGRESSIVE_THRESHOLD) * TaxRates.HIGH_PCT / 100.0;

        String notes = taxable > 0
                ? "Облагается превышение над вычетом %,.0f ₽ (1 млн × ключевая ставка %.2f%%)"
                        .formatted(deduction, keyRatePct)
                : "Проценты укладываются в необлагаемый вычет %,.0f ₽".formatted(deduction);
        return new DepositTaxBreakdown(interestIncome, deduction, taxable, tax, notes);
    }
}
