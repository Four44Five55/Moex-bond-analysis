package org.example.web.dto;

/**
 * Сводка по отобранным облигациям для дашборда.
 *
 * @param count                количество бумаг
 * @param averageYield         средняя доходность к погашению, %
 * @param averageReliability   средний балл надёжности (1..10)
 * @param investmentGradeShare доля инвестиционного уровня (0..1)
 * @param maxYield             максимальная доходность, %
 */
public record SummaryDto(
        long count,
        double averageYield,
        double averageReliability,
        double investmentGradeShare,
        double maxYield
) {
}
