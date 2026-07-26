package org.example.domain.portfolio.allocation;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationCalculatorTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-6);

    private final AllocationCalculator calculator = new AllocationCalculator();

    @Test
    void mergesCategoriesAndComputesShares() {
        List<AllocationSlice> slices = calculator.breakdown(List.of(
                new Holding("Облигации", 300),
                new Holding("Акции", 500),
                new Holding("Облигации", 200)));

        assertThat(slices).hasSize(2);
        assertThat(slices.get(0).category()).isEqualTo("Облигации");   // 500 — больше, идёт первой
        assertThat(slices.get(0).value()).isCloseTo(500.0, TOLERANCE);
        assertThat(slices.get(0).percent()).isCloseTo(50.0, TOLERANCE);
        assertThat(slices.get(1).percent()).isCloseTo(50.0, TOLERANCE);
    }

    @Test
    void sortsByValueDescending() {
        List<AllocationSlice> slices = calculator.breakdown(List.of(
                new Holding("A", 10),
                new Holding("B", 70),
                new Holding("C", 20)));

        assertThat(slices).extracting(AllocationSlice::category).containsExactly("B", "C", "A");
    }

    @Test
    void percentagesSumToHundred() {
        List<AllocationSlice> slices = calculator.breakdown(List.of(
                new Holding("A", 1),
                new Holding("B", 1),
                new Holding("C", 1)));

        assertThat(slices.stream().mapToDouble(AllocationSlice::percent).sum())
                .isCloseTo(100.0, TOLERANCE);
    }

    @Test
    void ignoresNonPositiveHoldings() {
        List<AllocationSlice> slices = calculator.breakdown(List.of(
                new Holding("Акции", 100),
                new Holding("Пустая", 0),
                new Holding("Отрицательная", -50)));

        assertThat(slices).extracting(AllocationSlice::category).containsExactly("Акции");
        assertThat(slices.get(0).percent()).isCloseTo(100.0, TOLERANCE);
    }

    @Test
    void emptyInputGivesEmptyBreakdown() {
        assertThat(calculator.breakdown(List.of())).isEmpty();
        assertThat(calculator.breakdown(List.of(new Holding("Пусто", 0)))).isEmpty();
    }
}
