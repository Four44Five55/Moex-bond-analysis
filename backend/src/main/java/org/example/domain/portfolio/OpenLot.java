package org.example.domain.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Открытый лот после FIFO-свёртки: {@code costPerUnit} включает долю комиссии покупки. */
public record OpenLot(BigDecimal quantity, BigDecimal costPerUnit, LocalDate buyDate) {

    public BigDecimal cost() {
        return quantity.multiply(costPerUnit);
    }
}
