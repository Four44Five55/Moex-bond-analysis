package org.example.domain.portfolio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Закрытая (проданная) часть лота по FIFO. Хранит даты покупки/продажи для ЛДВ,
 * себестоимость и выручку (с учётом комиссий).
 */
public record RealizedSale(
        double quantity,
        LocalDate buyDate,
        LocalDate sellDate,
        double cost,
        double proceeds
) {
    public double pnl() {
        return proceeds - cost;
    }

    /** Полных лет владения (для ЛДВ). */
    public long yearsHeld() {
        return ChronoUnit.YEARS.between(buyDate, sellDate);
    }
}
