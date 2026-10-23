package org.example.web.dto.portfolio;

import org.example.domain.model.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        double unrealizedPct,
        /** {@code DEFAULT} | {@code TECHNICAL} | {@code null} — бумага платит. */
        String defaultStatus,
        /** С этой даты выплаты по бумаге не считаются; {@code null} — платит. */
        LocalDate paymentsStoppedOn,
        /** Дату прекращения выплат назвал инвестор, а не подставило приложение. */
        boolean defaultConfirmed
) {
}
