package org.example.domain.portfolio.allocation;

import java.math.BigDecimal;

/**
 * Доля портфеля в одной категории: сумма стоимости и процент от итога.
 * <p>
 * Сумма — деньги, а процент — производная от неё величина для диаграммы, поэтому он остаётся
 * {@code double}: доли всё равно округляются до десятых при показе.
 */
public record AllocationSlice(String category, BigDecimal value, double percent) {
}
