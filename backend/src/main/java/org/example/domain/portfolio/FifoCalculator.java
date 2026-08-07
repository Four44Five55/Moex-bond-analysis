package org.example.domain.portfolio;

import org.example.domain.model.TradeSide;
import org.example.domain.money.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * FIFO-свёртка сделок по одной бумаге: открытые лоты + реализованные продажи.
 * Комиссии распределяются на единицу (покупка увеличивает себестоимость, продажа уменьшает выручку).
 * Продажа сверх имеющегося количества игнорируется в непокрытой части.
 * <p>
 * Раньше остаток лота сравнивался с эпсилоном 1e-9: в {@code double} после вычитания
 * {@code take} от количества оставался мусор порядка 1e-16, и лот никогда не закрывался ровно.
 * На {@link BigDecimal} вычитание точное, поэтому сравнение идёт с нулём — эпсилон больше
 * не нужен и, что важнее, не может однажды съесть настоящий дробный остаток.
 */
@Component
public class FifoCalculator {

    private static final class MutableLot {
        BigDecimal quantity;
        final BigDecimal costPerUnit;
        final LocalDate date;

        MutableLot(BigDecimal quantity, BigDecimal costPerUnit, LocalDate date) {
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
            if (!Money.isPositive(t.quantity())) {
                continue;
            }
            BigDecimal commissionPerUnit = Money.divide(t.commission(), t.quantity());

            if (t.side() == TradeSide.BUY) {
                lots.addLast(new MutableLot(
                        t.quantity(), t.moneyPerUnit().add(commissionPerUnit), t.date()));
            } else {
                BigDecimal proceedsPerUnit = t.moneyPerUnit().subtract(commissionPerUnit);
                BigDecimal remaining = t.quantity();
                while (Money.isPositive(remaining) && !lots.isEmpty()) {
                    MutableLot lot = lots.peekFirst();
                    BigDecimal take = Money.min(remaining, lot.quantity);
                    realized.add(new RealizedSale(
                            take, lot.date, t.date(),
                            take.multiply(lot.costPerUnit),
                            take.multiply(proceedsPerUnit)));
                    lot.quantity = lot.quantity.subtract(take);
                    remaining = remaining.subtract(take);
                    if (!Money.isPositive(lot.quantity)) {
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
