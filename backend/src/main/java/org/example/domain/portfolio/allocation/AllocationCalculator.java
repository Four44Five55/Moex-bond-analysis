package org.example.domain.portfolio.allocation;

import org.springframework.stereotype.Component;

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
        Map<String, Double> byCategory = new LinkedHashMap<>();
        for (Holding h : holdings) {
            if (h.value() > 0) {
                byCategory.merge(h.category(), h.value(), Double::sum);
            }
        }
        double total = byCategory.values().stream().mapToDouble(Double::doubleValue).sum();
        return byCategory.entrySet().stream()
                .map(e -> new AllocationSlice(e.getKey(), e.getValue(),
                        total > 0 ? e.getValue() / total * 100.0 : 0.0))
                .sorted(Comparator.comparingDouble(AllocationSlice::value).reversed())
                .toList();
    }
}
