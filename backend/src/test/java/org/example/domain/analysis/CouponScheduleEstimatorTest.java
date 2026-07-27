package org.example.domain.analysis;

import org.assertj.core.data.Offset;
import org.example.domain.model.CouponPayment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CouponScheduleEstimatorTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-6);

    private final CouponScheduleEstimator estimator = new CouponScheduleEstimator();

    private static ScheduledCoupon coupon(String start, String date, Double value) {
        return new ScheduledCoupon(LocalDate.parse(date), LocalDate.parse(start), 1000.0, value);
    }

    @Test
    void knownCouponsPassThroughUntouched() {
        List<CouponPayment> result = estimator.complete(List.of(
                coupon("2026-01-15", "2026-07-15", 34.9),
                coupon("2026-07-15", "2027-01-15", 34.9)));

        assertThat(result).extracting(CouponPayment::perBond).containsExactly(34.9, 34.9);
        assertThat(result).extracting(CouponPayment::estimated).containsOnly(false);
    }

    @Test
    void floaterFuturePeriodsAreEstimatedByTheLastKnownRate() {
        // Так выглядит ОФЗ-ПК: прошедшие купоны с суммой, будущие — пустые.
        // 49.5 ₽ за 92 дня на номинал 1000 → ≈19.64% годовых, столько же и на следующий период.
        List<CouponPayment> result = estimator.complete(List.of(
                coupon("2026-03-04", "2026-06-04", 49.5),
                coupon("2026-06-04", "2026-09-04", null),
                coupon("2026-09-04", "2026-12-04", null)));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).estimated()).isFalse();

        double impliedRate = 49.5 / 1000.0 * 365 / 92;
        assertThat(result.get(1).perBond()).isCloseTo(1000.0 * impliedRate * 92 / 365, TOLERANCE);
        assertThat(result.get(1).estimated()).isTrue();
        assertThat(result.get(2).estimated()).isTrue();
    }

    @Test
    void estimateFollowsTheAmortizedFaceValue() {
        // Номинал погашен наполовину — купон на него и считается, а не остаётся прежним
        List<ScheduledCoupon> schedule = List.of(
                coupon("2026-03-04", "2026-06-04", 50.0),
                new ScheduledCoupon(LocalDate.parse("2026-09-04"), LocalDate.parse("2026-06-04"),
                        500.0, null));

        List<CouponPayment> result = estimator.complete(schedule);

        // Период той же длины (92 дня), номинал вдвое меньше → и купон вдвое меньше
        assertThat(result.get(1).perBond()).isCloseTo(25.0, TOLERANCE);
    }

    @Test
    void longerPeriodGetsProportionallyBiggerCoupon() {
        List<ScheduledCoupon> schedule = List.of(
                coupon("2026-01-01", "2026-04-01", 24.65),                     // 90 дней
                new ScheduledCoupon(LocalDate.parse("2026-10-01"),
                        LocalDate.parse("2026-04-01"), 1000.0, null));         // 183 дня

        List<CouponPayment> result = estimator.complete(schedule);

        assertThat(result.get(1).perBond()).isCloseTo(24.65 * 183 / 90.0, TOLERANCE);
    }

    @Test
    void withoutPeriodBoundsTheLastKnownCouponIsRepeated() {
        List<CouponPayment> result = estimator.complete(List.of(
                new ScheduledCoupon(LocalDate.parse("2026-06-04"), null, 1000.0, 49.5),
                new ScheduledCoupon(LocalDate.parse("2026-09-04"), null, 1000.0, null)));

        assertThat(result.get(1).perBond()).isCloseTo(49.5, TOLERANCE);
        assertThat(result.get(1).estimated()).isTrue();
    }

    @Test
    void unknownCouponsWithoutAnyKnownOneAreDropped() {
        // Оценивать не от чего: выпуск размещён, но ни один купон ещё не зафиксирован
        List<CouponPayment> result = estimator.complete(List.of(
                coupon("2026-06-04", "2026-09-04", null),
                coupon("2026-09-04", "2026-12-04", null)));

        assertThat(result).isEmpty();
    }

    @Test
    void scheduleIsSortedAndRowsWithoutDateAreIgnored() {
        List<CouponPayment> result = estimator.complete(List.of(
                coupon("2026-07-15", "2027-01-15", 34.9),
                new ScheduledCoupon(null, LocalDate.parse("2026-01-15"), 1000.0, 34.9),
                coupon("2026-01-15", "2026-07-15", 34.9)));

        assertThat(result).extracting(CouponPayment::date)
                .containsExactly(LocalDate.parse("2026-07-15"), LocalDate.parse("2027-01-15"));
    }

    @Test
    void emptyScheduleGivesEmptyResult() {
        assertThat(estimator.complete(List.of())).isEmpty();
    }
}
