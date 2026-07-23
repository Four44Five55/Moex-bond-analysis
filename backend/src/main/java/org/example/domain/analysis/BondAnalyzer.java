package org.example.domain.analysis;

import org.example.domain.model.Bond;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Доменные расчёты по облигациям (агрегаты для сводки). Фильтрация/сортировка вынесены
 * в JPA Specification и выполняются на стороне БД.
 */
@Component
public class BondAnalyzer {

    public double calculateAverageYield(List<Bond> bonds) {
        return bonds.isEmpty() ? 0.0
                : bonds.stream().mapToDouble(Bond::getYieldToMaturity).average().orElse(0.0);
    }

    public double calculateAverageReliability(List<Bond> bonds) {
        return bonds.isEmpty() ? 0.0
                : bonds.stream().mapToInt(Bond::getReliabilityScore).average().orElse(0.0);
    }

    public double investmentGradeShare(List<Bond> bonds) {
        return bonds.isEmpty() ? 0.0
                : (double) bonds.stream().filter(Bond::isInvestmentGrade).count() / bonds.size();
    }

    public double maxYield(List<Bond> bonds) {
        return bonds.stream().mapToDouble(Bond::getYieldToMaturity).max().orElse(0.0);
    }
}
