package org.example.domain.portfolio;

import org.example.domain.money.Money;

import java.math.BigDecimal;
import java.util.List;

/** Результат FIFO-свёртки сделок по одной бумаге. */
public record FifoResult(List<OpenLot> openLots, List<RealizedSale> realizedSales) {

    public BigDecimal openQuantity() {
        return openLots.stream().map(OpenLot::quantity).reduce(Money.ZERO, BigDecimal::add);
    }

    public BigDecimal openCost() {
        return openLots.stream().map(OpenLot::cost).reduce(Money.ZERO, BigDecimal::add);
    }

    public BigDecimal realizedPnl() {
        return realizedSales.stream().map(RealizedSale::pnl).reduce(Money.ZERO, BigDecimal::add);
    }
}
