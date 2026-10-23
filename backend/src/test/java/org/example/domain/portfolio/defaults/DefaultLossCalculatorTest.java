package org.example.domain.portfolio.defaults;

import org.example.domain.model.AmortizationPayment;
import org.example.domain.model.BondDefaultFlags;
import org.example.domain.model.CouponPayment;
import org.example.domain.model.TradeSide;
import org.example.domain.money.Money;
import org.example.domain.portfolio.PricedTrade;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DefaultLossCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final LocalDate CUTOFF = LocalDate.of(2026, 8, 1);

    private final DefaultLossCalculator calculator = new DefaultLossCalculator();

    /** Ежемесячный купон 21,78 ₽ и погашение 1000 ₽ — как у МОНОП 1P04. */
    private static final List<CouponPayment> COUPONS = List.of(
            new CouponPayment(LocalDate.of(2026, 6, 10), 21.78),   // до дефолта — получен
            new CouponPayment(LocalDate.of(2026, 7, 10), 21.78),   // до дефолта — получен
            new CouponPayment(LocalDate.of(2026, 8, 9), 21.78),    // просрочен
            new CouponPayment(LocalDate.of(2026, 9, 8), 21.78),    // просрочен
            new CouponPayment(LocalDate.of(2026, 11, 8), 21.78));  // ещё впереди
    private static final List<AmortizationPayment> REDEMPTION = List.of(
            new AmortizationPayment(LocalDate.of(2026, 9, 8), 1000.0));

    private static PricedTrade trade(TradeSide side, LocalDate date, String qty, String price, String fee) {
        return new PricedTrade(side, date, Money.of(qty), Money.of(price), Money.of(fee));
    }

    @Test
    void splitsScheduleIntoReceivedMissedAndFuture() {
        List<PricedTrade> trades = List.of(trade(TradeSide.BUY, LocalDate.of(2026, 5, 1), "10", "1000", "5"));

        DefaultLoss loss = calculator.compute(trades, COUPONS, REDEMPTION, Money.ZERO,
                Money.of("38.70"), CUTOFF, TODAY);

        assertThat(loss.quantity()).isEqualByComparingTo("10");
        assertThat(loss.invested()).isEqualByComparingTo("10005");
        assertThat(loss.couponsReceived()).isEqualByComparingTo("435.6");     // 2 × 21,78 × 10
        assertThat(loss.missedCoupons()).isEqualByComparingTo("435.6");       // 2 × 21,78 × 10
        assertThat(loss.missedPrincipal()).isEqualByComparingTo("10000");
        assertThat(loss.futureCoupons()).isEqualByComparingTo("217.8");
        assertThat(loss.futurePrincipal()).isEqualByComparingTo("0");
        assertThat(loss.marketValue()).isEqualByComparingTo("387");
        // Своих денег потеряно: 10 005 вложено − 387 по рынку; купоны тело не возвращают
        assertThat(loss.capitalLoss()).isEqualByComparingTo("9618");
        assertThat(loss.netResult()).isEqualByComparingTo("-9182.4");
        assertThat(loss.recoveryRatePct()).isCloseTo(3.868, within(0.001));
    }

    @Test
    void missedPaymentsCountOnlyBondsHeldOnTheirDate() {
        // Половину продали в TQRD до погашения: по погашению просрочено только за оставшиеся
        List<PricedTrade> trades = List.of(
                trade(TradeSide.BUY, LocalDate.of(2026, 5, 1), "10", "1000", "0"),
                trade(TradeSide.SELL, LocalDate.of(2026, 8, 20), "5", "100", "1"));

        DefaultLoss loss = calculator.compute(trades, COUPONS, REDEMPTION, Money.ZERO,
                Money.of("38.70"), CUTOFF, TODAY);

        assertThat(loss.saleProceeds()).isEqualByComparingTo("499");
        assertThat(loss.missedCoupons()).isEqualByComparingTo("326.7");       // 21,78 × 10 + 21,78 × 5
        assertThat(loss.missedPrincipal()).isEqualByComparingTo("5000");
        assertThat(loss.futureCoupons()).isEqualByComparingTo("108.9");
        assertThat(loss.capitalLoss()).isEqualByComparingTo("9307.5");       // 10 000 − 499 − 193,5
    }

    @Test
    void recoveryReducesCapitalLoss() {
        List<PricedTrade> trades = List.of(trade(TradeSide.BUY, LocalDate.of(2026, 5, 1), "10", "1000", "0"));

        DefaultLoss loss = calculator.compute(trades, COUPONS, REDEMPTION, Money.of("3000"),
                Money.of("38.70"), CUTOFF, TODAY);

        assertThat(loss.capitalReturned()).isEqualByComparingTo("3000");
        assertThat(loss.capitalLoss()).isEqualByComparingTo("6613");
    }

    @Test
    void stateWithoutMarkStopsOnlyFuturePaymentsWhenMoexReportsDefault() {
        DefaultState auto = DefaultState.of(new BondDefaultFlags(true, true), null, null, TODAY);
        assertThat(auto.cutoff()).isEqualTo(TODAY);
        assertThat(auto.confirmed()).isFalse();
        assertThat(auto.paid(TODAY.minusDays(1))).isTrue();
        assertThat(auto.paid(TODAY)).isFalse();

        DefaultState technicalOnly = DefaultState.of(new BondDefaultFlags(false, true), null, null, TODAY);
        assertThat(technicalOnly.defaulted()).isFalse();
        assertThat(technicalOnly.flagged()).isTrue();

        DefaultState marked = DefaultState.of(BondDefaultFlags.NONE, CUTOFF, null, TODAY);
        assertThat(marked.cutoff()).isEqualTo(CUTOFF);
        assertThat(marked.confirmed()).isTrue();
    }
}
