package org.example.domain.portfolio;

import org.example.domain.model.AmortizationPayment;
import org.example.domain.model.CouponPayment;
import org.example.domain.model.DividendPayment;
import org.example.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncomeForecasterTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 7, 25);

    private final IncomeForecaster forecaster = new IncomeForecaster();

    @Test
    void scalesBondPaymentsByQuantityAndKeepsOnlyFutureOnes() {
        List<CouponPayment> coupons = List.of(
                new CouponPayment(LocalDate.of(2025, 1, 15), 34.9),   // в прошлом — не прогноз
                new CouponPayment(LocalDate.of(2025, 7, 25), 34.9),   // сегодня — входит
                new CouponPayment(LocalDate.of(2026, 1, 15), 34.9));
        List<AmortizationPayment> amortizations = List.of(
                new AmortizationPayment(LocalDate.of(2026, 1, 15), 1000.0));

        List<IncomeEvent> events = forecaster.bondIncome("SU26240", Money.of("10"), coupons, amortizations, TODAY);

        assertThat(events).hasSize(3);
        assertThat(events).filteredOn(e -> e.kind() == IncomeKind.COUPON)
                .allSatisfy(e -> assertThat(e.gross()).isEqualByComparingTo("349"));
        assertThat(events).filteredOn(e -> e.kind() == IncomeKind.AMORTIZATION)
                .singleElement()
                .satisfies(e -> assertThat(e.gross()).isEqualByComparingTo("10000"));
        assertThat(events).allMatch(e -> !e.estimate());
    }

    @Test
    void dividendsAreForecastOnlyFromAnnouncedRecordDates() {
        List<DividendPayment> dividends = List.of(
                new DividendPayment(LocalDate.of(2025, 5, 10), 25.58, "RUB"),  // прошедшая
                new DividendPayment(LocalDate.of(2025, 10, 9), 12.5, "RUB"));

        List<IncomeEvent> events = forecaster.dividendIncome("VTBR", Money.of("200"), dividends, TODAY);

        // Прикидка «по прошлогодней выплате» намеренно не строится — только объявленное MOEX
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.date()).isEqualTo(LocalDate.of(2025, 10, 9));
            assertThat(e.gross()).isEqualByComparingTo("2500");
            assertThat(e.estimate()).isFalse();
        });
    }

    @Test
    void dividendEventsAreSortedByDate() {
        List<DividendPayment> dividends = List.of(
                new DividendPayment(LocalDate.of(2026, 5, 1), 10.0, "RUB"),
                new DividendPayment(LocalDate.of(2025, 12, 1), 8.0, "RUB"));

        List<IncomeEvent> events = forecaster.dividendIncome("SBER", Money.of("1"), dividends, TODAY);

        assertThat(events).extracting(IncomeEvent::date)
                .containsExactly(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 5, 1));
    }

    @Test
    void closedPositionProducesNoDividendIncome() {
        List<DividendPayment> dividends = List.of(
                new DividendPayment(LocalDate.of(2025, 10, 9), 12.5, "RUB"));

        assertThat(forecaster.dividendIncome("VTBR", Money.ZERO, dividends, TODAY)).isEmpty();
    }

    @Test
    void maturedBondProducesNoIncome() {
        List<CouponPayment> pastCoupons = List.of(new CouponPayment(LocalDate.of(2024, 3, 1), 40.0));

        assertThat(forecaster.bondIncome("OLD", Money.of("5"), pastCoupons, List.of(), TODAY)).isEmpty();
    }
}
