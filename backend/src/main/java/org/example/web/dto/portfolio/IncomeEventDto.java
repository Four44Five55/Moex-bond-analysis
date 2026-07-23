package org.example.web.dto.portfolio;

import org.example.domain.portfolio.IncomeKind;

import java.time.LocalDate;

public record IncomeEventDto(
        LocalDate date,
        String secId,
        IncomeKind kind,
        double gross,
        double net,
        boolean estimate
) {
}
