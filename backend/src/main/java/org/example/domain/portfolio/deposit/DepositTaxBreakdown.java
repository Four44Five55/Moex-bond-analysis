package org.example.domain.portfolio.deposit;

/**
 * Налог с процентов по вкладам за календарный год.
 *
 * @param interestIncome проценты, полученные за год по всем вкладам
 * @param deduction      необлагаемая сумма (1 млн × ключевая ставка)
 * @param taxableIncome  облагаемое превышение
 * @param tax            НДФЛ с превышения
 */
public record DepositTaxBreakdown(
        double interestIncome,
        double deduction,
        double taxableIncome,
        double tax,
        String notes
) {

    public static DepositTaxBreakdown zero() {
        return new DepositTaxBreakdown(0, 0, 0, 0, "Вкладов нет");
    }
}
