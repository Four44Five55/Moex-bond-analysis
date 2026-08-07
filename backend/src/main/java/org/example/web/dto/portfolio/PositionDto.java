package org.example.web.dto.portfolio;

import org.example.domain.model.InstrumentType;

import java.math.BigDecimal;

public record PositionDto(
        String secId,
        InstrumentType type,
        String name,
        BigDecimal quantity,
        BigDecimal avgCost,
        BigDecimal invested,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        /** Доля, а не деньги: относительная величина, её точность ограничена котировкой. */
        double unrealizedPct
) {
}
