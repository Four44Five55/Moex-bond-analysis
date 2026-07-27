package org.example.domain.portfolio.calendar;

import org.assertj.core.data.Offset;
import org.example.domain.portfolio.IncomeKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCalendarCalculatorTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-6);
    private static final YearMonth START = YearMonth.of(2026, 8);

    private final PaymentCalendarCalculator calculator = new PaymentCalendarCalculator();

    private static PaymentCalendarEntry coupon(String date, double gross) {
        return new PaymentCalendarEntry(LocalDate.parse(date), IncomeKind.COUPON, gross, gross * 0.87);
    }

    @Test
    void groupsPaymentsByMonth() {
        PaymentCalendar calendar = calculator.build(List.of(
                coupon("2026-08-05", 100),
                coupon("2026-08-20", 50),
                coupon("2026-10-01", 200)), START, 3);

        assertThat(calendar.months()).extracting(PaymentCalendarMonth::gross)
                .containsExactly(150.0, 0.0, 200.0);
        assertThat(calendar.months().get(0).count()).isEqualTo(2);
        assertThat(calendar.totalGross()).isCloseTo(350.0, TOLERANCE);
        assertThat(calendar.totalNet()).isCloseTo(350.0 * 0.87, TOLERANCE);
    }

    @Test
    void keepsEmptyMonthsInsideHorizon() {
        PaymentCalendar calendar = calculator.build(List.of(coupon("2026-09-10", 100)), START, 4);

        assertThat(calendar.months()).extracting(PaymentCalendarMonth::month)
                .containsExactly(START, START.plusMonths(1), START.plusMonths(2), START.plusMonths(3));
        assertThat(calendar.months().get(3).byKind()).isEmpty();
    }

    @Test
    void dropsPaymentsOutsideHorizon() {
        PaymentCalendar calendar = calculator.build(List.of(
                coupon("2026-07-31", 999),   // месяцем раньше начала
                coupon("2026-09-01", 100),
                coupon("2026-10-01", 999)),  // за концом горизонта
                START, 2);

        assertThat(calendar.totalGross()).isCloseTo(100.0, TOLERANCE);
        assertThat(calendar.months()).hasSize(2);
    }

    @Test
    void splitsMonthByKindInEnumOrder() {
        PaymentCalendar calendar = calculator.build(List.of(
                new PaymentCalendarEntry(LocalDate.parse("2026-08-20"), IncomeKind.INTEREST, 40, 40),
                new PaymentCalendarEntry(LocalDate.parse("2026-08-15"), IncomeKind.DIVIDEND, 30, 26),
                coupon("2026-08-10", 100),
                coupon("2026-08-12", 20)), START, 1);

        List<PaymentKindSlice> byKind = calendar.months().get(0).byKind();
        assertThat(byKind).extracting(PaymentKindSlice::kind)
                .containsExactly(IncomeKind.COUPON, IncomeKind.DIVIDEND, IncomeKind.INTEREST);
        assertThat(byKind.get(0).gross()).isCloseTo(120.0, TOLERANCE);
        assertThat(byKind.stream().mapToDouble(PaymentKindSlice::gross).sum())
                .isCloseTo(calendar.months().get(0).gross(), TOLERANCE);
    }

    @Test
    void averagesOverWholeHorizonNotOverPaidMonths() {
        PaymentCalendar calendar = calculator.build(List.of(
                new PaymentCalendarEntry(LocalDate.parse("2026-08-05"), IncomeKind.COUPON, 120, 120)),
                START, 12);

        assertThat(calendar.averageMonthlyNet()).isCloseTo(10.0, TOLERANCE);
    }

    @Test
    void emptyHorizonGivesEmptyCalendar() {
        PaymentCalendar calendar = calculator.build(List.of(coupon("2026-08-05", 100)), START, 0);

        assertThat(calendar.months()).isEmpty();
        assertThat(calendar.totalGross()).isZero();
        assertThat(calendar.averageMonthlyNet()).isZero();
    }
}
