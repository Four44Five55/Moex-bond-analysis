package org.example.domain.analysis;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnsCalculatorTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-6);
    private static final LocalDate TODAY = LocalDate.of(2025, 7, 25);

    private final ReturnsCalculator calculator = new ReturnsCalculator();

    private PricePoint point(LocalDate date, double close) {
        return new PricePoint(date, close, close, close);
    }

    @Test
    void computesChangesAgainstEachPeriodBase() {
        List<PricePoint> points = List.of(
                point(LocalDate.of(2024, 12, 31), 100),  // база YTD
                point(LocalDate.of(2025, 6, 25), 120),   // база 1М
                point(LocalDate.of(2025, 7, 18), 125),   // база 1Н
                point(LocalDate.of(2025, 7, 24), 130),   // база 1Д
                point(TODAY, 143)
        );

        PerformanceMetrics m = calculator.compute(points, TODAY);

        assertThat(m.lastPrice()).isEqualTo(143.0);
        assertThat(m.change1d()).isCloseTo(10.0, TOLERANCE);       // 130 → 143
        assertThat(m.change1w()).isCloseTo(14.4, TOLERANCE);       // 125 → 143
        assertThat(m.change1m()).isCloseTo(19.166666, Offset.offset(1e-5));
        assertThat(m.changeYtd()).isCloseTo(43.0, TOLERANCE);      // 100 → 143
    }

    @Test
    void usesLastTradingDayBeforeTargetWhenExactDateMissing() {
        // Ровно неделю назад (18 июля) торгов не было — берётся ближайший предыдущий день
        List<PricePoint> points = List.of(
                point(LocalDate.of(2025, 7, 16), 200),
                point(TODAY, 220)
        );

        PerformanceMetrics m = calculator.compute(points, TODAY);

        assertThat(m.change1w()).isCloseTo(10.0, TOLERANCE);
    }

    @Test
    void leavesPeriodNullWhenHistoryIsShorterThanPeriod() {
        List<PricePoint> points = List.of(
                point(LocalDate.of(2025, 7, 24), 100),
                point(TODAY, 110)
        );

        PerformanceMetrics m = calculator.compute(points, TODAY);

        assertThat(m.change1d()).isCloseTo(10.0, TOLERANCE);
        // Истории меньше недели — базы для остальных периодов нет
        assertThat(m.change1w()).isNull();
        assertThat(m.change1m()).isNull();
        assertThat(m.change1y()).isNull();
        assertThat(m.changeYtd()).isNull();
    }

    @Test
    void rangeCoversOnlyLastFiftyTwoWeeks() {
        List<PricePoint> points = List.of(
                new PricePoint(LocalDate.of(2024, 5, 1), 900, 500, 700),  // старше года — вне окна
                new PricePoint(LocalDate.of(2025, 3, 1), 320, 280, 300),
                new PricePoint(TODAY, 310, 240, 250)
        );

        PerformanceMetrics m = calculator.compute(points, TODAY);

        assertThat(m.high52w()).isEqualTo(320.0);
        assertThat(m.low52w()).isEqualTo(240.0);
    }

    @Test
    void unsortedInputIsOrderedBeforeComputation() {
        List<PricePoint> points = List.of(
                point(TODAY, 150),
                point(LocalDate.of(2025, 7, 24), 100)
        );

        PerformanceMetrics m = calculator.compute(points, TODAY);

        assertThat(m.lastPrice()).isEqualTo(150.0);
        assertThat(m.change1d()).isCloseTo(50.0, TOLERANCE);
    }

    @Test
    void zeroBasePriceDoesNotProduceInfinity() {
        List<PricePoint> points = List.of(
                point(LocalDate.of(2025, 7, 24), 0),
                point(TODAY, 100)
        );

        assertThat(calculator.compute(points, TODAY).change1d()).isNull();
    }

    @Test
    void emptyHistoryGivesEmptyMetrics() {
        assertThat(calculator.compute(List.of(), TODAY)).isEqualTo(PerformanceMetrics.empty());
        assertThat(calculator.compute(null, TODAY)).isEqualTo(PerformanceMetrics.empty());
    }
}
