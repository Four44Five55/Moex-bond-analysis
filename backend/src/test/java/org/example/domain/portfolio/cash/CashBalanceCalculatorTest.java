package org.example.domain.portfolio.cash;

import org.example.domain.model.TradeSide;
import org.example.domain.portfolio.PricedTrade;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CashBalanceCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    private static final double EPS = 1e-9;

    private final CashBalanceCalculator calculator = new CashBalanceCalculator();

    @Test
    void withoutTradesBalanceIsWhatWasDeposited() {
        assertThat(calculator.balance(100_000, List.of(), 0)).isEqualTo(100_000);
    }

    @Test
    void purchaseTakesBothNotionalAndCommission() {
        PricedTrade buy = new PricedTrade(TradeSide.BUY, DAY, 10, 1_000, 50);

        assertThat(calculator.balance(100_000, List.of(buy), 0))
                .isCloseTo(100_000 - 10_000 - 50, org.assertj.core.data.Offset.offset(EPS));
    }

    @Test
    void saleReturnsMoneyMinusCommission() {
        PricedTrade buy = new PricedTrade(TradeSide.BUY, DAY, 10, 1_000, 50);
        PricedTrade sell = new PricedTrade(TradeSide.SELL, DAY.plusDays(30), 10, 1_200, 60);

        // 100 000 − 10 000 − 50 + 12 000 − 60
        assertThat(calculator.balance(100_000, List.of(buy, sell), 0))
                .isCloseTo(101_890, org.assertj.core.data.Offset.offset(EPS));
    }

    @Test
    void receivedPayoutsIncreaseBalance() {
        PricedTrade buy = new PricedTrade(TradeSide.BUY, DAY, 10, 1_000, 0);

        assertThat(calculator.balance(100_000, List.of(buy), 3_500))
                .isCloseTo(93_500, org.assertj.core.data.Offset.offset(EPS));
    }

    @Test
    void goesNegativeWhenTradesAreEnteredWithoutAnyDeposit() {
        // Так выглядит счёт, заведённый до появления учёта денег: сделки есть, пополнений нет.
        // Это не ошибка расчёта, и калькулятор не обязан её скрывать.
        PricedTrade buy = new PricedTrade(TradeSide.BUY, DAY, 10, 1_000, 50);

        assertThat(calculator.balance(0, List.of(buy), 0)).isNegative();
    }

    @Test
    void withdrawalsReduceBalance() {
        assertThat(calculator.balance(100_000 - 30_000, List.of(), 0)).isEqualTo(70_000);
    }
}
