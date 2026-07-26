package org.example.domain.portfolio.history;

import java.time.LocalDate;

/**
 * Точка ряда стоимости портфеля.
 *
 * @param value    рыночная стоимость открытых позиций на конец дня
 * @param invested чистый вложенный капитал накопительно (покупки с комиссией минус выручка от продаж);
 *                 может стать отрицательным, если из портфеля выведено больше, чем вложено
 */
public record ValuePoint(LocalDate date, double value, double invested) {

    /** Прибыль «на бумаге» относительно вложенного капитала. */
    public double pnl() {
        return value - invested;
    }
}
