package org.example;

import lombok.Data;

@Data
public class FilterCriteria {
    private double minYield = 5.0;              // Минимальная доходность
    private double maxDuration = 10.0;          // Максимальная дюрация
    private boolean onlyInvestmentGrade = false; // Только инвестиционный уровень
    private int minReliabilityScore = 5;        // Минимальный балл надежности
    private int limit = 100;                    // Лимит результатов
}