package org.example.domain.portfolio.allocation;

import org.assertj.core.data.Offset;
import org.example.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationCalculatorTest {

    /** Доли остаются {@code double} — это производная величина для диаграммы, а не деньги. */
    private static final Offset<Double> TOLERANCE = Offset.offset(1e-6);

    private final AllocationCalculator calculator = new AllocationCalculator();

    private static Holding holding(String category, String value) {
        return new Holding(category, Money.of(value));
    }

    @Test
    void mergesCategoriesAndComputesShares() {
        List<AllocationSlice> slices = calculator.breakdown(List.of(
                holding("Облигации", "300"),
                holding("Акции", "500"),
                holding("Облигации", "200")));

        assertThat(slices).hasSize(2);
        assertThat(slices.get(0).category()).isEqualTo("Облигации");   // 500 — больше, идёт первой
        assertThat(slices.get(0).value()).isEqualByComparingTo("500");
        assertThat(slices.get(0).percent()).isCloseTo(50.0, TOLERANCE);
        assertThat(slices.get(1).percent()).isCloseTo(50.0, TOLERANCE);
    }

    @Test
    void sortsByValueDescending() {
        List<AllocationSlice> slices = calculator.breakdown(List.of(
                holding("A", "10"),
                holding("B", "70"),
                holding("C", "20")));

        assertThat(slices).extracting(AllocationSlice::category).containsExactly("B", "C", "A");
    }

    @Test
    void percentagesSumToHundred() {
        List<AllocationSlice> slices = calculator.breakdown(List.of(
                holding("A", "1"),
                holding("B", "1"),
                holding("C", "1")));

        assertThat(slices.stream().mapToDouble(AllocationSlice::percent).sum())
                .isCloseTo(100.0, TOLERANCE);
    }

    @Test
    void ignoresNonPositiveHoldings() {
        List<AllocationSlice> slices = calculator.breakdown(List.of(
                holding("Акции", "100"),
                holding("Пустая", "0"),
                holding("Отрицательная", "-50")));

        assertThat(slices).extracting(AllocationSlice::category).containsExactly("Акции");
        assertThat(slices.get(0).percent()).isCloseTo(100.0, TOLERANCE);
    }

    @Test
    void emptyInputGivesEmptyBreakdown() {
        assertThat(calculator.breakdown(List.of())).isEmpty();
        assertThat(calculator.breakdown(List.of(holding("Пусто", "0")))).isEmpty();
    }
}
