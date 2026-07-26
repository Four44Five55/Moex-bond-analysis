package org.example.domain.analysis;

import java.time.LocalDate;

/** Точка дневного ценового ряда для расчёта доходностей: high/low/close за день. */
public record PricePoint(LocalDate date, double high, double low, double close) {
}
