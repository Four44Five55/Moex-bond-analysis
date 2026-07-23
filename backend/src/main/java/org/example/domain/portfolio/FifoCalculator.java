package org.example.domain.portfolio;

import org.example.domain.model.TradeSide;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * FIFO-свёртка сделок по одной бумаге: открытые лоты + реализованные продажи.
 * Комиссии распределяются на единицу (покупка увеличивает себестоимость, продажа уменьшает выручку).
 * Продажа сверх имеющегося количества игнорируется в непокрытой части.
 */
@Component
public class FifoCalculator {

    private static final class MutableLot {
        double quantity;
        final double costPerUnit;
        final LocalDate date;

        MutableLot(double quantity, double costPerUnit, LocalDate date) {
            this.quantity = quantity;
            this.costPerUnit = costPerUnit;
            this.date = date;
        }
    }

    /** Сделки должны быть отсортированы по дате (репозиторий уже сортирует). */
    public FifoResult process(List<PricedTrade> trades) {
        Deque<MutableLot> lots = new ArrayDeque<>();
        List<RealizedSale> realized = new ArrayList<>();

        for (PricedTrade t : trades) {
            if (t.quantity() <= 0) {
                continue;
            }
            double commissionPerUnit = t.commission() / t.quantity();

            if (t.side() == TradeSide.BUY) {
                lots.addLast(new MutableLot(t.quantity(), t.moneyPerUnit() + commissionPerUnit, t.date()));
            } else {
                double proceedsPerUnit = t.moneyPerUnit() - commissionPerUnit;
                double remaining = t.quantity();
                while (remaining > 1e-9 && !lots.isEmpty()) {
                    MutableLot lot = lots.peekFirst();
                    double take = Math.min(remaining, lot.quantity);
                    realized.add(new RealizedSale(
                            take, lot.date, t.date(),
                            take * lot.costPerUnit,
                            take * proceedsPerUnit));
                    lot.quantity -= take;
                    remaining -= take;
                    if (lot.quantity <= 1e-9) {
                        lots.removeFirst();
                    }
                }
            }
        }

        List<OpenLot> openLots = new ArrayList<>();
        for (MutableLot lot : lots) {
            openLots.add(new OpenLot(lot.quantity, lot.costPerUnit, lot.date));
        }
        return new FifoResult(openLots, realized);
    }
}
