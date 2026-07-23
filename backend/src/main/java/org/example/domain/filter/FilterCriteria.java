package org.example.domain.filter;

import lombok.Builder;
import lombok.Data;
import org.example.domain.model.InstrumentType;

/**
 * Критерии поиска/фильтрации инструментов. Собирается билдером в web-слое из query-параметров
 * и транслируется в JPA Specification ({@link InstrumentSpecifications}).
 */
@Data
@Builder
public class FilterCriteria {

    /** null — оба типа (облигации и акции). */
    private InstrumentType type;

    /** Полнотекстовый фрагмент: по названию/ISIN/коду. */
    private String query;

    // --- Только для облигаций ---
    @Builder.Default
    private double minYield = 0.0;
    @Builder.Default
    private double maxDuration = 100.0;
    @Builder.Default
    private int minReliabilityScore = 1;
    @Builder.Default
    private boolean onlyInvestmentGrade = false;
    /** Исключать бумаги с аномальной доходностью. */
    @Builder.Default
    private boolean excludeSuspicious = true;

    // --- Только для акций ---
    private String sector;
}
