package org.example.domain.analysis;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Чистый расчёт доходностей инструмента из дневного ценового ряда:
 * изменение за 1Д/1Н/1М/1Г/YTD (в %) и 52-недельный min/max.
 * Не обращается к БД/сети — легко тестируется независимо от инфраструктуры.
 */
@Component
public class ReturnsCalculator {

    public PerformanceMetrics compute(List<PricePoint> points, LocalDate today) {
        if (points == null || points.isEmpty()) {
            return PerformanceMetrics.empty();
        }
        List<PricePoint> sorted = points.stream()
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();
        double last = sorted.get(sorted.size() - 1).close();

        Double prevClose = sorted.size() >= 2 ? sorted.get(sorted.size() - 2).close() : null;
        Double c1d = change(last, prevClose);
        Double c1w = change(last, closeAsOf(sorted, today.minusWeeks(1)));
        Double c1m = change(last, closeAsOf(sorted, today.minusMonths(1)));
        Double c1y = change(last, closeAsOf(sorted, today.minusYears(1)));
        Double ytd = change(last, closeAsOf(sorted, LocalDate.of(today.getYear() - 1, 12, 31)));

        LocalDate windowStart = today.minusYears(1);
        Double high = null;
        Double low = null;
        for (PricePoint p : sorted) {
            if (p.date().isBefore(windowStart)) {
                continue;
            }
            high = high == null ? p.high() : Math.max(high, p.high());
            low = low == null ? p.low() : Math.min(low, p.low());
        }
        return new PerformanceMetrics(last, c1d, c1w, c1m, c1y, ytd, high, low);
    }

    /** Закрытие последнего торгового дня с датой ≤ target, либо null, если такого нет. */
    private Double closeAsOf(List<PricePoint> sorted, LocalDate target) {
        for (int i = sorted.size() - 1; i >= 0; i--) {
            if (!sorted.get(i).date().isAfter(target)) {
                return sorted.get(i).close();
            }
        }
        return null;
    }

    /** Изменение в процентах от базы к последней цене; null при отсутствии/нулевой базе. */
    private Double change(double last, Double base) {
        if (base == null || base == 0) {
            return null;
        }
        return (last - base) / base * 100.0;
    }
}
