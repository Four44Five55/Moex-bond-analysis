package org.example.domain.portfolio;

import java.util.List;

/** Результат FIFO-свёртки сделок по одной бумаге. */
public record FifoResult(List<OpenLot> openLots, List<RealizedSale> realizedSales) {

    public double openQuantity() {
        return openLots.stream().mapToDouble(OpenLot::quantity).sum();
    }

    public double openCost() {
        return openLots.stream().mapToDouble(OpenLot::cost).sum();
    }

    public double realizedPnl() {
        return realizedSales.stream().mapToDouble(RealizedSale::pnl).sum();
    }
}
