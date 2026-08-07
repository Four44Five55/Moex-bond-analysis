package org.example.domain.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Закрытая (проданная) часть лота по FIFO. Хранит даты покупки/продажи для ЛДВ,
 * себестоимость и выручку (с учётом комиссий).
 */
public record RealizedSale(
        BigDecimal quantity,
        LocalDate buyDate,
        LocalDate sellDate,
        BigDecimal cost,
        BigDecimal proceeds
) {
    public BigDecimal pnl() {
        return proceeds.subtract(cost);
    }

    /** Полных лет владения (для ЛДВ). */
    public long yearsHeld() {
        return ChronoUnit.YEARS.between(buyDate, sellDate);
    }
}
