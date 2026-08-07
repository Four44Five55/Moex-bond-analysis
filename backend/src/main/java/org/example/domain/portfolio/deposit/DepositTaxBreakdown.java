package org.example.domain.portfolio.deposit;

import org.example.domain.money.Money;

import java.math.BigDecimal;

/**
 * Налог с процентов по вкладам за календарный год.
 *
 * @param interestIncome проценты, полученные за год по всем вкладам
 * @param deduction      необлагаемая сумма (1 млн × ключевая ставка)
 * @param taxableIncome  облагаемое превышение
 * @param tax            НДФЛ с превышения
 * @param keyRatePct     ставка, из которой посчитан вычет — величина приходит из ЦБ и меняется,
 *                       поэтому её видно рядом с результатом, а не только в логах
 */
public record DepositTaxBreakdown(
        BigDecimal interestIncome,
        BigDecimal deduction,
        BigDecimal taxableIncome,
        BigDecimal tax,
        BigDecimal keyRatePct,
        String notes
) {

    public static DepositTaxBreakdown zero() {
        return new DepositTaxBreakdown(
                Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, "Вкладов нет");
    }
}
