package org.example.domain.portfolio.allocation;

/** Доля портфеля в одной категории: сумма стоимости и процент от итога. */
public record AllocationSlice(String category, double value, double percent) {
}
