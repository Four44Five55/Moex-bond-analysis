package org.example.domain.portfolio.deposit;

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
        double interestIncome,
        double deduction,
        double taxableIncome,
        double tax,
        double keyRatePct,
        String notes
) {

    public static DepositTaxBreakdown zero() {
        return new DepositTaxBreakdown(0, 0, 0, 0, 0, "Вкладов нет");
    }
}
