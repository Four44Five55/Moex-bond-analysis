package org.example.domain.portfolio.calendar;

import org.example.domain.portfolio.IncomeKind;

import java.math.BigDecimal;

/** Часть месяца, приходящаяся на один вид выплат. */
public record PaymentKindSlice(IncomeKind kind, BigDecimal gross, BigDecimal net) {
}
