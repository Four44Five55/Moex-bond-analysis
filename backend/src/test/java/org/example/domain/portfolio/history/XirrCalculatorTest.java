package org.example.domain.portfolio.history;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XirrCalculatorTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-4);

    private final XirrCalculator calculator = new XirrCalculator();

    @Test
    void twoFlowCaseMatchesClosedFormSolution() {
        // Удвоение за 366 дней (2024 високосный): (1+r)^(366/365) = 2 → r = 2^(365/366) − 1
        List<CashFlow> flows = List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), -1000),
                new CashFlow(LocalDate.of(2025, 1, 1), 2000)
        );

        assertThat(calculator.compute(flows))
                .isCloseTo(Math.pow(2, 365.0 / 366.0) - 1, TOLERANCE);
    }

    @Test
    void solvesIrregularFlows() {
        // Значение проверено независимым расчётом: NPV при этой ставке ≈ 7e-12
        List<CashFlow> flows = List.of(
                new CashFlow(LocalDate.of(2023, 1, 1), -10_000),
                new CashFlow(LocalDate.of(2023, 3, 15), -5_000),
                new CashFlow(LocalDate.of(2023, 9, 20), 3_000),
                new CashFlow(LocalDate.of(2024, 1, 1), 13_500)
        );

        assertThat(calculator.compute(flows)).isCloseTo(0.11414, TOLERANCE);
    }

    @Test
    void lossProducesNegativeRate() {
        // Падение до 80% за 366 дней: r = 0.8^(365/366) − 1
        List<CashFlow> flows = List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), -1000),
                new CashFlow(LocalDate.of(2025, 1, 1), 800)
        );

        Double rate = calculator.compute(flows);

        assertThat(rate).isNotNull();
        assertThat(rate).isCloseTo(Math.pow(0.8, 365.0 / 366.0) - 1, TOLERANCE);
    }

    @Test
    void npvIsZeroAtComputedRate() {
        // Свойство решения: подстановка ставки обнуляет NPV, каким бы методом её ни нашли
        List<CashFlow> flows = List.of(
                new CashFlow(LocalDate.of(2022, 5, 4), -250_000),
                new CashFlow(LocalDate.of(2022, 11, 30), -40_000),
                new CashFlow(LocalDate.of(2023, 6, 1), 15_000),
                new CashFlow(LocalDate.of(2024, 2, 17), 90_000),
                new CashFlow(LocalDate.of(2025, 7, 25), 260_000)
        );

        Double rate = calculator.compute(flows);
        assertThat(rate).isNotNull();

        LocalDate start = LocalDate.of(2022, 5, 4);
        double npv = flows.stream()
                .mapToDouble(f -> f.amount() / Math.pow(1 + rate,
                        java.time.temporal.ChronoUnit.DAYS.between(start, f.date()) / 365.0))
                .sum();
        assertThat(npv).isCloseTo(0.0, Offset.offset(1e-4));
    }

    @Test
    void survivesTotalLossWithoutDivergence() {
        // Почти полная потеря капитала — область, где метод Ньютона улетает за r ≤ −1
        List<CashFlow> flows = List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), -100_000),
                new CashFlow(LocalDate.of(2025, 1, 1), 1)
        );

        Double rate = calculator.compute(flows);

        assertThat(rate).isNotNull();
        assertThat(rate).isCloseTo(Math.pow(1.0 / 100_000, 365.0 / 366.0) - 1, TOLERANCE);
    }

    @Test
    void returnsNullWhenFlowsHaveSingleSign() {
        List<CashFlow> onlyPurchases = List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), -1000),
                new CashFlow(LocalDate.of(2024, 6, 1), -500)
        );

        assertThat(calculator.compute(onlyPurchases)).isNull();
        assertThat(calculator.compute(List.of())).isNull();
        assertThat(calculator.compute(null)).isNull();
    }
}
