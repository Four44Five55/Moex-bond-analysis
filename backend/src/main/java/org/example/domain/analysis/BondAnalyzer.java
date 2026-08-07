package org.example.domain.analysis;

import org.example.domain.model.Bond;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Доменные расчёты по облигациям (агрегаты для сводки). Фильтрация/сортировка вынесены
 * в JPA Specification и выполняются на стороне БД.
 */
@Component
public class BondAnalyzer {

    /**
     * Выпуски без доходности в среднее не входят — они его не занижают.
     * <p>
     * Раньше поле было примитивным, и у бумаги без сделок там стоял ноль, который попадал
     * в выборку наравне с настоящими значениями: сотня неликвидов утягивала среднюю
     * доходность портфеля вниз на ровном месте. Теперь такие выпуски отсеиваются.
     */
    public double calculateAverageYield(List<Bond> bonds) {
        return bonds.stream()
                .map(Bond::getYieldToMaturity)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.0);
    }

    public double calculateAverageReliability(List<Bond> bonds) {
        return bonds.stream()
                .map(Bond::getReliabilityScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average().orElse(0.0);
    }

    public double investmentGradeShare(List<Bond> bonds) {
        return bonds.isEmpty() ? 0.0
                : (double) bonds.stream().filter(Bond::isInvestmentGrade).count() / bonds.size();
    }

    public double maxYield(List<Bond> bonds) {
        return bonds.stream()
                .map(Bond::getYieldToMaturity)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max().orElse(0.0);
    }
}
