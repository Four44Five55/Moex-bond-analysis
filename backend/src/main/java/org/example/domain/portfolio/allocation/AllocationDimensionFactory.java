package org.example.domain.portfolio.allocation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Выбирает {@link AllocationDimension} по ключу из запроса. Все реализации Spring
 * внедряет списком автоматически — добавление измерения не требует правок здесь (OCP).
 */
@Component
public class AllocationDimensionFactory {

    private final Map<String, AllocationDimension> byKey;

    public AllocationDimensionFactory(List<AllocationDimension> dimensions) {
        this.byKey = dimensions.stream()
                .collect(Collectors.toMap(AllocationDimension::key, d -> d));
    }

    public AllocationDimension forKey(String key) {
        AllocationDimension dimension = byKey.get(key == null ? "" : key.toLowerCase());
        if (dimension == null) {
            throw new IllegalArgumentException(
                    "Неизвестное измерение аллокации: " + key + " (допустимо: " + byKey.keySet() + ")");
        }
        return dimension;
    }
}
