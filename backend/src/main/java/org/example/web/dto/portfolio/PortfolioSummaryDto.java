package org.example.web.dto.portfolio;

/**
 * @param cashBalance свободные деньги: пополнения − выводы − покупки − комиссии + продажи + выплаты.
 *                    Может быть отрицательным, если сделки заведены, а пополнения — нет.
 * @param totalValue  весь капитал счёта: бумаги по рынку плюс свободные деньги
 */
public record PortfolioSummaryDto(
        double invested,
        double marketValue,
        double cashBalance,
        double totalValue,
        double unrealizedPnl,
        double realizedPnl,
        double totalCommission,
        double receivedIncome,
        double projectedAnnualIncome,
        double estimatedTax,
        int positionsCount
) {
}
