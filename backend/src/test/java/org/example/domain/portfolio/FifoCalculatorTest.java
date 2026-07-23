package org.example.domain.portfolio;

import org.example.domain.model.TradeSide;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FifoCalculatorTest {

    private final FifoCalculator calculator = new FifoCalculator();

    @Test
    void computesRealizedPnlAndOpenLotsWithCommissions() {
        // BUY 10 @250 (комиссия 2.5), SELL 4 @300 (комиссия 1.2)
        List<PricedTrade> trades = List.of(
                new PricedTrade(TradeSide.BUY, LocalDate.of(2024, 2, 1), 10, 250, 2.5),
                new PricedTrade(TradeSide.SELL, LocalDate.of(2024, 6, 1), 4, 300, 1.2)
        );

        FifoResult result = calculator.process(trades);

        // costPerUnit = 250 + 0.25; proceedsPerUnit = 300 - 0.3
        assertThat(result.realizedPnl()).isCloseTo(197.8, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(result.openQuantity()).isEqualTo(6.0);
        assertThat(result.openCost()).isCloseTo(1501.5, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void sellBeyondHoldingsIgnoresUncoveredPart() {
        List<PricedTrade> trades = List.of(
                new PricedTrade(TradeSide.BUY, LocalDate.of(2024, 1, 1), 5, 100, 0),
                new PricedTrade(TradeSide.SELL, LocalDate.of(2024, 2, 1), 8, 120, 0)
        );

        FifoResult result = calculator.process(trades);

        assertThat(result.openQuantity()).isZero();
        assertThat(result.realizedSales()).hasSize(1);
        assertThat(result.realizedSales().get(0).quantity()).isEqualTo(5.0);
    }
}
