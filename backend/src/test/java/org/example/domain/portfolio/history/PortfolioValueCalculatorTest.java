package org.example.domain.portfolio.history;

import org.assertj.core.data.Offset;
import org.example.domain.model.TradeSide;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioValueCalculatorTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-6);

    private final PortfolioValueCalculator calculator = new PortfolioValueCalculator();

    @Test
    void valuesOpenPositionAtDailyClose() {
        PortfolioValueRequest request = PortfolioValueRequest
                .builder(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3))
                .trades(List.of(new DatedTrade("SBER", TradeSide.BUY,
                        LocalDate.of(2025, 1, 1), 10, 250, 5)))
                .closes("SBER", Map.of(
                        LocalDate.of(2025, 1, 1), 250.0,
                        LocalDate.of(2025, 1, 2), 260.0,
                        LocalDate.of(2025, 1, 3), 240.0))
                .build();

        List<ValuePoint> series = calculator.series(request);

        assertThat(series).hasSize(3);
        assertThat(series).extracting(ValuePoint::value)
                .containsExactly(2500.0, 2600.0, 2400.0);
        // Вложено = 10 × 250 + комиссия 5, и по дням не меняется
        assertThat(series).extracting(ValuePoint::invested)
                .containsExactly(2505.0, 2505.0, 2505.0);
        assertThat(series.get(1).pnl()).isCloseTo(95.0, TOLERANCE);
    }

    @Test
    void appliesTradesMadeBeforeRequestedPeriod() {
        PortfolioValueRequest request = PortfolioValueRequest
                .builder(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 2))
                .trades(List.of(new DatedTrade("GAZP", TradeSide.BUY,
                        LocalDate.of(2024, 3, 10), 100, 150, 0)))
                .closes("GAZP", Map.of(LocalDate.of(2025, 5, 30), 170.0))
                .build();

        List<ValuePoint> series = calculator.series(request);

        // Позиция открыта до начала периода, цена — с последнего торгового дня (30 мая)
        assertThat(series).extracting(ValuePoint::value).containsExactly(17_000.0, 17_000.0);
        assertThat(series).extracting(ValuePoint::invested).containsExactly(15_000.0, 15_000.0);
    }

    @Test
    void saleReducesQuantityAndReturnsCapital() {
        PortfolioValueRequest request = PortfolioValueRequest
                .builder(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 3))
                .trades(List.of(
                        new DatedTrade("LKOH", TradeSide.BUY, LocalDate.of(2025, 1, 1), 10, 100, 0),
                        new DatedTrade("LKOH", TradeSide.SELL, LocalDate.of(2025, 1, 2), 4, 120, 0)))
                .closes("LKOH", Map.of(
                        LocalDate.of(2025, 1, 1), 100.0,
                        LocalDate.of(2025, 1, 2), 120.0,
                        LocalDate.of(2025, 1, 3), 130.0))
                .build();

        List<ValuePoint> series = calculator.series(request);

        assertThat(series).extracting(ValuePoint::value)
                .containsExactly(1000.0, 720.0, 780.0);   // 10, затем 6 бумаг
        assertThat(series).extracting(ValuePoint::invested)
                .containsExactly(1000.0, 520.0, 520.0);   // продажа вернула 480
    }

    @Test
    void fullExitLeavesZeroValue() {
        PortfolioValueRequest request = PortfolioValueRequest
                .builder(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2))
                .trades(List.of(
                        new DatedTrade("VTBR", TradeSide.BUY, LocalDate.of(2025, 1, 1), 50, 80, 0),
                        new DatedTrade("VTBR", TradeSide.SELL, LocalDate.of(2025, 1, 2), 50, 90, 0)))
                .closes("VTBR", Map.of(
                        LocalDate.of(2025, 1, 1), 80.0,
                        LocalDate.of(2025, 1, 2), 90.0))
                .build();

        List<ValuePoint> series = calculator.series(request);

        assertThat(series.get(1).value()).isZero();
        // Вложено ушло в минус: выведено на 500 больше, чем внесено
        assertThat(series.get(1).invested()).isCloseTo(-500.0, TOLERANCE);
    }

    @Test
    void missingPriceHistoryContributesNothing() {
        PortfolioValueRequest request = PortfolioValueRequest
                .builder(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1))
                .trades(List.of(new DatedTrade("NEW", TradeSide.BUY,
                        LocalDate.of(2025, 1, 1), 10, 100, 0)))
                .build();

        List<ValuePoint> series = calculator.series(request);

        assertThat(series).singleElement().satisfies(p -> {
            assertThat(p.value()).isZero();
            assertThat(p.invested()).isCloseTo(1000.0, TOLERANCE);
        });
    }

    @Test
    void rejectsInvertedPeriod() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> PortfolioValueRequest
                .builder(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 1, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
