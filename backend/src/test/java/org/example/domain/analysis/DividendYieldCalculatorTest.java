package org.example.domain.analysis;

import org.assertj.core.data.Offset;
import org.example.domain.model.DividendPayment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DividendYieldCalculatorTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-6);
    private static final LocalDate TODAY = LocalDate.of(2025, 7, 25);

    private final DividendYieldCalculator calculator = new DividendYieldCalculator();

    @Test
    void sumsPaymentsInsideTrailingYearAndDividesByPrice() {
        List<DividendPayment> dividends = List.of(
                new DividendPayment(LocalDate.of(2024, 12, 20), 20.0, "RUB"),
                new DividendPayment(LocalDate.of(2025, 6, 10), 30.0, "RUB"));

        DividendSummary s = calculator.trailing(dividends, 500.0, TODAY);

        assertThat(s.trailingSum()).isCloseTo(50.0, TOLERANCE);
        assertThat(s.yieldPct()).isCloseTo(10.0, TOLERANCE);
        assertThat(s.count()).isEqualTo(2);
    }

    @Test
    void excludesPaymentsOlderThanYearAndFutureOnes() {
        List<DividendPayment> dividends = List.of(
                new DividendPayment(LocalDate.of(2024, 7, 24), 99.0, "RUB"),   // на день старше окна
                new DividendPayment(LocalDate.of(2024, 7, 25), 10.0, "RUB"),   // ровно граница — входит
                new DividendPayment(LocalDate.of(2025, 9, 1), 77.0, "RUB"));   // будущая отсечка

        DividendSummary s = calculator.trailing(dividends, 100.0, TODAY);

        assertThat(s.trailingSum()).isCloseTo(10.0, TOLERANCE);
        assertThat(s.count()).isEqualTo(1);
    }

    @Test
    void yieldIsNullWithoutPriceButSumSurvives() {
        List<DividendPayment> dividends = List.of(
                new DividendPayment(LocalDate.of(2025, 5, 5), 15.0, "RUB"));

        DividendSummary s = calculator.trailing(dividends, 0.0, TODAY);

        assertThat(s.trailingSum()).isCloseTo(15.0, TOLERANCE);
        assertThat(s.yieldPct()).isNull();
    }

    @Test
    void skipsPaymentsWithoutRecordDate() {
        List<DividendPayment> dividends = Arrays.asList(
                new DividendPayment(null, 50.0, "RUB"),
                new DividendPayment(LocalDate.of(2025, 3, 3), 5.0, "RUB"));

        DividendSummary s = calculator.trailing(dividends, 100.0, TODAY);

        assertThat(s.trailingSum()).isCloseTo(5.0, TOLERANCE);
        assertThat(s.count()).isEqualTo(1);
    }

    @Test
    void noPaymentsInWindowGivesEmptySummary() {
        List<DividendPayment> old = List.of(
                new DividendPayment(LocalDate.of(2020, 1, 1), 40.0, "RUB"));

        assertThat(calculator.trailing(old, 100.0, TODAY)).isEqualTo(DividendSummary.empty());
        assertThat(calculator.trailing(List.of(), 100.0, TODAY)).isEqualTo(DividendSummary.empty());
        assertThat(calculator.trailing(null, 100.0, TODAY)).isEqualTo(DividendSummary.empty());
    }
}
