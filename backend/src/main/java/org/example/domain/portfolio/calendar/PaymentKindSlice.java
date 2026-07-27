package org.example.domain.portfolio.calendar;

import org.example.domain.portfolio.IncomeKind;

/** Часть месяца, приходящаяся на один вид выплат. */
public record PaymentKindSlice(IncomeKind kind, double gross, double net) {
}
