package org.example.domain.portfolio;

import org.example.domain.model.TradeSide;
import org.example.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Суммы сверяются через {@code isEqualByComparingTo}, а не {@code isEqualTo}: у
 * {@link java.math.BigDecimal} равенство учитывает масштаб, и посчитанные 197.800000 не равны
 * ожидаемым 197.8, хотя это одно число.
 * <p>
 * Допуска здесь больше нет — на десятичной арифметике эти величины получаются точно,
 * и любое расхождение означает ошибку расчёта, а не накопленную погрешность.
 */
class FifoCalculatorTest {

    private final FifoCalculator calculator = new FifoCalculator();

    @Test
    void computesRealizedPnlAndOpenLotsWithCommissions() {
        // BUY 10 @250 (комиссия 2.5), SELL 4 @300 (комиссия 1.2)
        List<PricedTrade> trades = List.of(
                new PricedTrade(TradeSide.BUY, LocalDate.of(2024, 2, 1),
                        Money.of("10"), Money.of("250"), Money.of("2.5")),
                new PricedTrade(TradeSide.SELL, LocalDate.of(2024, 6, 1),
                        Money.of("4"), Money.of("300"), Money.of("1.2"))
        );

        FifoResult result = calculator.process(trades);

        // costPerUnit = 250 + 0.25; proceedsPerUnit = 300 - 0.3
        assertThat(result.realizedPnl()).isEqualByComparingTo("197.8");
        assertThat(result.openQuantity()).isEqualByComparingTo("6");
        assertThat(result.openCost()).isEqualByComparingTo("1501.5");
    }

    @Test
    void sellBeyondHoldingsIgnoresUncoveredPart() {
        List<PricedTrade> trades = List.of(
                new PricedTrade(TradeSide.BUY, LocalDate.of(2024, 1, 1),
                        Money.of("5"), Money.of("100"), Money.ZERO),
                new PricedTrade(TradeSide.SELL, LocalDate.of(2024, 2, 1),
                        Money.of("8"), Money.of("120"), Money.ZERO)
        );

        FifoResult result = calculator.process(trades);

        assertThat(result.openQuantity()).isEqualByComparingTo("0");
        assertThat(result.realizedSales()).hasSize(1);
        assertThat(result.realizedSales().get(0).quantity()).isEqualByComparingTo("5");
    }

    @Test
    void lotClosesExactlyWithoutEpsilonSlack() {
        // Дробное количество, которое в double не представимо точно: 3 × 0.1 там даёт
        // 0.30000000000000004, и остаток лота никогда не обнулялся ровно — отсюда и был эпсилон.
        List<PricedTrade> trades = List.of(
                new PricedTrade(TradeSide.BUY, LocalDate.of(2024, 1, 1),
                        Money.of("0.1"), Money.of("100"), Money.ZERO),
                new PricedTrade(TradeSide.BUY, LocalDate.of(2024, 1, 2),
                        Money.of("0.2"), Money.of("100"), Money.ZERO),
                new PricedTrade(TradeSide.SELL, LocalDate.of(2024, 3, 1),
                        Money.of("0.3"), Money.of("100"), Money.ZERO)
        );

        FifoResult result = calculator.process(trades);

        assertThat(result.openLots()).isEmpty();
        assertThat(result.openQuantity()).isEqualByComparingTo("0");
        assertThat(result.realizedPnl()).isEqualByComparingTo("0");
    }
}
