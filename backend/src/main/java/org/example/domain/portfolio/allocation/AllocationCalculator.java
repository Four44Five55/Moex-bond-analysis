package org.example.domain.portfolio.allocation;

import org.example.domain.money.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Чистая агрегация аллокации: суммирует стоимость по категориям, считает проценты
 * и сортирует по убыванию. Не зависит от {@link AllocationDimension} — классификацию
 * делает вызывающий, здесь только математика долей.
 */
@Component
public class AllocationCalculator {

    public List<AllocationSlice> breakdown(List<Holding> holdings) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Holding h : holdings) {
            if (Money.isPositive(h.value())) {
                byCategory.merge(h.category(), h.value(), BigDecimal::add);
            }
        }
        BigDecimal total = Money.sum(byCategory.values());
        return byCategory.entrySet().stream()
                .map(e -> new AllocationSlice(e.getKey(), Money.round(e.getValue()),
                        Money.percentOf(e.getValue(), total)))
                .sorted(Comparator.comparing(AllocationSlice::value).reversed())
                .toList();
    }
}
