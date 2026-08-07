package org.example.web.dto.portfolio;

import org.example.domain.portfolio.IncomeKind;

import java.math.BigDecimal;

/** Доля одного вида выплат в месяце календаря. */
public record CalendarKindDto(IncomeKind kind, BigDecimal gross, BigDecimal net) {
}
