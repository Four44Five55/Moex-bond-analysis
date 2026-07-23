package org.example.web.dto.portfolio;

import org.example.domain.model.InstrumentType;

public record PositionDto(
        String secId,
        InstrumentType type,
        String name,
        double quantity,
        double avgCost,
        double invested,
        double currentPrice,
        double marketValue,
        double unrealizedPnl,
        double unrealizedPct
) {
}
