package org.example.application.portfolio;

import org.assertj.core.data.Offset;
import org.example.domain.model.Bond;
import org.example.domain.model.Share;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentPricingTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

    private static Bond bond(double price, double accruedInterest) {
        Bond bond = new Bond();
        bond.setSecId("RU000A0TEST1");
        bond.setFaceValue(1000.0);
        bond.setLastPrice(price);
        bond.setAccruedInterest(accruedInterest);
        return bond;
    }

    @Test
    void bondQuoteIsPercentOfFaceValue() {
        assertThat(InstrumentPricing.moneyPerUnit(bond(98.5, 0), 98.5)).isCloseTo(985.0, TOLERANCE);
    }

    @Test
    void tradePriceIncludesAccruedInterest() {
        assertThat(InstrumentPricing.tradeMoneyPerUnit(bond(98.5, 0), 98.5, 12.34))
                .isCloseTo(985.0 + 12.34, TOLERANCE);
    }

    @Test
    void positionValueIsDirtyPriceSoItMatchesTheCostBasis() {
        // Купили по 98.5 + НКД 12.34, цена не изменилась → позиция не в убытке на размер НКД
        Bond bond = bond(98.5, 12.34);

        double cost = InstrumentPricing.tradeMoneyPerUnit(bond, 98.5, 12.34);
        assertThat(InstrumentPricing.currentPositionMoneyPerUnit(bond)).isCloseTo(cost, TOLERANCE);
        assertThat(InstrumentPricing.currentMoneyPerUnit(bond)).isCloseTo(985.0, TOLERANCE);
    }

    @Test
    void unknownPriceStaysZeroAndDoesNotBecomeBareAccruedInterest() {
        Bond bond = bond(0, 12.34);
        bond.setLastPrice(null);

        assertThat(InstrumentPricing.currentPositionMoneyPerUnit(bond)).isZero();
    }

    @Test
    void sharesHaveNoAccruedInterest() {
        Share share = new Share();
        share.setSecId("SBER");
        share.setLastPrice(250.0);

        assertThat(InstrumentPricing.currentPositionMoneyPerUnit(share)).isCloseTo(250.0, TOLERANCE);
        assertThat(InstrumentPricing.tradeMoneyPerUnit(share, 250.0, 0)).isCloseTo(250.0, TOLERANCE);
    }
}
