package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BondAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(BondAnalyzer.class);

    public List<Bond> filterBonds(List<Bond> bonds, FilterCriteria criteria) {
        return bonds.stream()
                .filter(b -> b.getYieldToMaturity() >= criteria.getMinYield())
                .filter(b -> b.getDuration() <= criteria.getMaxDuration())
                .filter(b -> !criteria.isOnlyInvestmentGrade() || b.isInvestmentGrade())
                .filter(b -> b.getReliabilityScore() >= criteria.getMinReliabilityScore())
                .sorted(Comparator.comparing(Bond::getReliabilityScore).reversed()
                        .thenComparing(Comparator.comparing(Bond::getYieldToMaturity).reversed()))
                .limit(criteria.getLimit())
                .collect(Collectors.toList());
    }

    /**
     * Расчет средней доходности отобранных облигаций
     * @param bonds список облигаций
     * @return средняя доходность в процентах
     */
    public double calculateAverageYield(List<Bond> bonds) {
        if (bonds == null || bonds.isEmpty()) {
            logger.warn("Attempt to calculate average yield for empty bond list");
            return 0.0;
        }

        double sum = bonds.stream()
                .mapToDouble(Bond::getYieldToMaturity)
                .sum();

        double average = sum / bonds.size();
        logger.debug("Calculated average yield: {:.2f}% for {} bonds", average, bonds.size());

        return average;
    }

    /**
     * Расчет среднего балла надежности
     * @param bonds список облигаций
     * @return средний балл надежности (1-10)
     */
    public double calculateAverageReliability(List<Bond> bonds) {
        if (bonds == null || bonds.isEmpty()) {
            return 0.0;
        }
        return bonds.stream()
                .mapToInt(Bond::getReliabilityScore)
                .average()
                .orElse(0.0);
    }

    public List<Bond> filterByInvestmentGrade(List<Bond> bonds) {
        return bonds.stream()
                .filter(Bond::isInvestmentGrade)
                .collect(Collectors.toList());
    }

    public List<Bond> filterByHighReliability(List<Bond> bonds, int minScore) {
        return bonds.stream()
                .filter(b -> b.getReliabilityScore() >= minScore)
                .sorted(Comparator.comparingInt(Bond::getReliabilityScore).reversed())
                .collect(Collectors.toList());
    }
}