package org.example.application.portfolio;

import org.example.domain.model.Bond;
import org.example.domain.model.Share;
import org.example.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentPricingTest {

    private static Bond bond(Double price, double accruedInterest) {
        Bond bond = new Bond();
        bond.setSecId("RU000A0TEST1");
        bond.setFaceValue(1000.0);
        bond.setLastPrice(price);
        bond.setAccruedInterest(accruedInterest);
        return bond;
    }

    @Test
    void bondQuoteIsPercentOfFaceValue() {
        assertThat(InstrumentPricing.moneyPerUnit(bond(98.5, 0), Money.of("98.5")))
                .isEqualByComparingTo("985");
    }

    @Test
    void tradePriceIncludesAccruedInterest() {
        assertThat(InstrumentPricing.tradeMoneyPerUnit(
                bond(98.5, 0), Money.of("98.5"), Money.of("12.34")))
                .isEqualByComparingTo("997.34");
    }

    @Test
    void positionValueIsDirtyPriceSoItMatchesTheCostBasis() {
        // Купили по 98.5 + НКД 12.34, цена не изменилась → позиция не в убытке на размер НКД
        Bond bond = bond(98.5, 12.34);

        BigDecimal cost = InstrumentPricing.tradeMoneyPerUnit(
                bond, Money.of("98.5"), Money.of("12.34"));
        assertThat(InstrumentPricing.currentPositionMoneyPerUnit(bond)).isEqualByComparingTo(cost);
        assertThat(InstrumentPricing.currentMoneyPerUnit(bond)).isEqualByComparingTo("985");
    }

    @Test
    void unknownPriceStaysZeroAndDoesNotBecomeBareAccruedInterest() {
        Bond bond = bond(null, 12.34);

        assertThat(InstrumentPricing.currentPositionMoneyPerUnit(bond)).isEqualByComparingTo("0");
    }

    @Test
    void sharesHaveNoAccruedInterest() {
        Share share = new Share();
        share.setSecId("SBER");
        share.setLastPrice(250.0);

        assertThat(InstrumentPricing.currentPositionMoneyPerUnit(share))
                .isEqualByComparingTo("250");
        assertThat(InstrumentPricing.tradeMoneyPerUnit(share, Money.of("250"), Money.ZERO))
                .isEqualByComparingTo("250");
    }
}
