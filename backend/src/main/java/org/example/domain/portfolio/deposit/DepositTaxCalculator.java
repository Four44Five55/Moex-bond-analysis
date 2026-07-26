package org.example.domain.portfolio.deposit;

import org.example.domain.portfolio.tax.TaxRates;
import org.springframework.stereotype.Component;

import java.util.Locale;

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

    /** Пояснение к расчёту — русский текст, значит и числа в нём форматируются по-русски. */
    private static final Locale RU = Locale.of("ru");

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

        // Ставка называется в обеих ветках: без неё непонятно, откуда взялась сумма вычета.
        // Локаль задана явно: у formatted() она берётся из настроек JVM, и один и тот же расчёт
        // выводил бы «20,00%» на машине разработчика и «20.00%» на сервере с английской локалью.
        String template = taxable > 0
                ? "Облагается превышение над вычетом %,.0f ₽ (1 млн × ключевая ставка %.2f%%)"
                : "Проценты укладываются в необлагаемый вычет %,.0f ₽ (1 млн × ключевая ставка %.2f%%)";
        String notes = String.format(RU, template, deduction, keyRatePct);
        return new DepositTaxBreakdown(interestIncome, deduction, taxable, tax, keyRatePct, notes);
    }
}
