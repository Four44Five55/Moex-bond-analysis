package org.example.domain.portfolio;

import java.time.LocalDate;

/** Открытый лот после FIFO-свёртки: {@code costPerUnit} включает долю комиссии покупки. */
public record OpenLot(double quantity, double costPerUnit, LocalDate buyDate) {

    public double cost() {
        return quantity * costPerUnit;
    }
}
