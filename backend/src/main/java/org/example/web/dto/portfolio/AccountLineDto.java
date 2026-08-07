package org.example.web.dto.portfolio;

import org.example.domain.model.AccountType;
import org.example.domain.model.IisType;

import java.math.BigDecimal;

/** Строка счёта в сводке по всем счетам: сколько там лежит и как оно себя ведёт. */
public record AccountLineDto(
        Long id,
        String name,
        AccountType type,
        IisType iisType,
        BigDecimal invested,
        BigDecimal marketValue,
        BigDecimal cashBalance,
        BigDecimal totalValue,
        BigDecimal unrealizedPnl,
        double unrealizedPct,
        int positionsCount
) {
}
