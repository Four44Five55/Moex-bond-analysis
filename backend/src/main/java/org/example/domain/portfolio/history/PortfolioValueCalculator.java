package org.example.domain.portfolio.history;

import org.example.domain.model.TradeSide;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Чистое построение дневного ряда «стоимость портфеля / вложенный капитал»: сделки
 * прокручиваются по дням, позиции оцениваются ценой закрытия ближайшего торгового дня.
 * <p>
 * Сделки применяются одним проходом синхронно с календарём (обе последовательности
 * упорядочены по дате), поэтому сложность — O(дней + сделок), а не O(дней × сделок).
 * Без БД и сети: цены передаются готовыми в {@link PortfolioValueRequest}.
 */
@Component
public class PortfolioValueCalculator {

    private static final double QUANTITY_EPSILON = 1e-9;

    public List<ValuePoint> series(PortfolioValueRequest request) {
        List<DatedTrade> trades = request.trades().stream()
                .sorted(Comparator.comparing(DatedTrade::date))
                .toList();

        Map<String, Double> quantityBySecId = new HashMap<>();
        double invested = 0;
        int applied = 0;

        List<ValuePoint> series = new ArrayList<>();
        for (LocalDate day = request.from(); !day.isAfter(request.to()); day = day.plusDays(1)) {
            // Все сделки по этот день включительно — в том числе совершённые до начала периода
            while (applied < trades.size() && !trades.get(applied).date().isAfter(day)) {
                DatedTrade trade = trades.get(applied++);
                quantityBySecId.merge(trade.secId(), signedQuantity(trade), Double::sum);
                invested += investedDelta(trade);
            }
            series.add(new ValuePoint(day, marketValue(request, quantityBySecId, day), invested));
        }
        return series;
    }

    private double signedQuantity(DatedTrade trade) {
        return trade.side() == TradeSide.BUY ? trade.quantity() : -trade.quantity();
    }

    /** Покупка выводит деньги из кармана (вместе с комиссией), продажа возвращает (за вычетом комиссии). */
    private double investedDelta(DatedTrade trade) {
        double gross = trade.quantity() * trade.moneyPerUnit();
        return trade.side() == TradeSide.BUY
                ? gross + trade.commission()
                : -(gross - trade.commission());
    }

    private double marketValue(PortfolioValueRequest request,
                               Map<String, Double> quantityBySecId,
                               LocalDate day) {
        double total = 0;
        for (Map.Entry<String, Double> position : quantityBySecId.entrySet()) {
            double quantity = position.getValue();
            if (quantity > QUANTITY_EPSILON) {
                total += quantity * request.closeAsOf(position.getKey(), day);
            }
        }
        return total;
    }
}
