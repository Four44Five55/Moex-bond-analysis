package org.example.web.dto.portfolio;

import org.example.domain.model.AccountType;
import org.example.domain.model.IisType;

/** Строка счёта в сводке по всем счетам: сколько там лежит и как оно себя ведёт. */
public record AccountLineDto(
        Long id,
        String name,
        AccountType type,
        IisType iisType,
        double invested,
        double marketValue,
        double cashBalance,
        double totalValue,
        double unrealizedPnl,
        double unrealizedPct,
        int positionsCount
) {
}
