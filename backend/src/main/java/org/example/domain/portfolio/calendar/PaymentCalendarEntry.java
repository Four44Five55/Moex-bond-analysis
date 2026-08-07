package org.example.domain.portfolio.calendar;

import org.example.domain.portfolio.IncomeKind;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Одна выплата на входе календаря. Умышленно уже, чем событие web-слоя: календарю нужны
 * только дата, вид и суммы — бумага, счёт и признак оценки идут мимо, их показывает список выплат.
 */
public record PaymentCalendarEntry(LocalDate date, IncomeKind kind, BigDecimal gross, BigDecimal net) {
}
