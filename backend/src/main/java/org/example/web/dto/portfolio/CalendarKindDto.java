package org.example.web.dto.portfolio;

import org.example.domain.portfolio.IncomeKind;

/** Доля одного вида выплат в месяце календаря. */
public record CalendarKindDto(IncomeKind kind, double gross, double net) {
}
