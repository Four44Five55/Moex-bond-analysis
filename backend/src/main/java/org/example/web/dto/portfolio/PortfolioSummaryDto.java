package org.example.web.dto.portfolio;

public record PortfolioSummaryDto(
        double invested,
        double marketValue,
        double unrealizedPnl,
        double realizedPnl,
        double totalCommission,
        double projectedAnnualIncome,
        double estimatedTax,
        int positionsCount
) {
}
