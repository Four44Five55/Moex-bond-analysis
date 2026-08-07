package org.example.domain.portfolio.calendar;

import org.example.domain.money.Money;
import org.example.domain.portfolio.IncomeKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCalendarCalculatorTest {

    private static final YearMonth START = YearMonth.of(2026, 8);

    private final PaymentCalendarCalculator calculator = new PaymentCalendarCalculator();

    /** Купон «на руки» — минус 13% НДФЛ. */
    private static PaymentCalendarEntry coupon(String date, String gross) {
        BigDecimal amount = Money.of(gross);
        return new PaymentCalendarEntry(LocalDate.parse(date), IncomeKind.COUPON,
                amount, amount.multiply(Money.of("0.87")));
    }

    private static PaymentCalendarEntry entry(String date, IncomeKind kind, String gross, String net) {
        return new PaymentCalendarEntry(LocalDate.parse(date), kind, Money.of(gross), Money.of(net));
    }

    @Test
    void groupsPaymentsByMonth() {
        PaymentCalendar calendar = calculator.build(List.of(
                coupon("2026-08-05", "100"),
                coupon("2026-08-20", "50"),
                coupon("2026-10-01", "200")), START, 3);

        assertThat(calendar.months()).hasSize(3);
        assertThat(calendar.months().get(0).gross()).isEqualByComparingTo("150");
        assertThat(calendar.months().get(1).gross()).isEqualByComparingTo("0");
        assertThat(calendar.months().get(2).gross()).isEqualByComparingTo("200");
        assertThat(calendar.months().get(0).count()).isEqualTo(2);
        assertThat(calendar.totalGross()).isEqualByComparingTo("350");
        assertThat(calendar.totalNet()).isEqualByComparingTo("304.5");
    }

    @Test
    void keepsEmptyMonthsInsideHorizon() {
        PaymentCalendar calendar = calculator.build(
                List.of(coupon("2026-09-10", "100")), START, 4);

        assertThat(calendar.months()).extracting(PaymentCalendarMonth::month)
                .containsExactly(START, START.plusMonths(1), START.plusMonths(2), START.plusMonths(3));
        assertThat(calendar.months().get(3).byKind()).isEmpty();
    }

    @Test
    void dropsPaymentsOutsideHorizon() {
        PaymentCalendar calendar = calculator.build(List.of(
                coupon("2026-07-31", "999"),   // месяцем раньше начала
                coupon("2026-09-01", "100"),
                coupon("2026-10-01", "999")),  // за концом горизонта
                START, 2);

        assertThat(calendar.totalGross()).isEqualByComparingTo("100");
        assertThat(calendar.months()).hasSize(2);
    }

    @Test
    void splitsMonthByKindInEnumOrder() {
        PaymentCalendar calendar = calculator.build(List.of(
                entry("2026-08-20", IncomeKind.INTEREST, "40", "40"),
                entry("2026-08-15", IncomeKind.DIVIDEND, "30", "26"),
                coupon("2026-08-10", "100"),
                coupon("2026-08-12", "20")), START, 1);

        List<PaymentKindSlice> byKind = calendar.months().get(0).byKind();
        assertThat(byKind).extracting(PaymentKindSlice::kind)
                .containsExactly(IncomeKind.COUPON, IncomeKind.DIVIDEND, IncomeKind.INTEREST);
        assertThat(byKind.get(0).gross()).isEqualByComparingTo("120");
        assertThat(byKind.stream().map(PaymentKindSlice::gross).reduce(Money.ZERO, BigDecimal::add))
                .isEqualByComparingTo(calendar.months().get(0).gross());
    }

    @Test
    void averagesOverWholeHorizonNotOverPaidMonths() {
        PaymentCalendar calendar = calculator.build(
                List.of(entry("2026-08-05", IncomeKind.COUPON, "120", "120")), START, 12);

        assertThat(calendar.averageMonthlyNet()).isEqualByComparingTo("10");
    }

    @Test
    void amortizationCountsAsIncomingMoneyButNotAsIncome() {
        // Возврат номинала легко больше самих купонов — свести их в одну строку «доход» нельзя
        PaymentCalendar calendar = calculator.build(List.of(
                coupon("2026-08-05", "100"),
                entry("2026-08-05", IncomeKind.AMORTIZATION, "5000", "5000")), START, 3);

        assertThat(calendar.totalGross()).isEqualByComparingTo("5100");
        assertThat(calendar.principalReturn()).isEqualByComparingTo("5000");
        assertThat(calendar.incomeGross()).isEqualByComparingTo("100");
    }

    @Test
    void totalsByKindCoverTheWholeHorizon() {
        PaymentCalendar calendar = calculator.build(List.of(
                coupon("2026-08-05", "100"),
                coupon("2026-10-05", "120"),
                entry("2026-09-01", IncomeKind.DIVIDEND, "300", "261")), START, 3);

        assertThat(calendar.totalByKind()).extracting(PaymentKindSlice::kind)
                .containsExactly(IncomeKind.COUPON, IncomeKind.DIVIDEND);
        assertThat(calendar.totalByKind().get(0).gross()).isEqualByComparingTo("220");
        assertThat(calendar.principalReturn()).isEqualByComparingTo("0");
    }

    @Test
    void emptyHorizonGivesEmptyCalendar() {
        PaymentCalendar calendar = calculator.build(
                List.of(coupon("2026-08-05", "100")), START, 0);

        assertThat(calendar.months()).isEmpty();
        assertThat(calendar.totalGross()).isEqualByComparingTo("0");
        assertThat(calendar.averageMonthlyNet()).isEqualByComparingTo("0");
    }
}
