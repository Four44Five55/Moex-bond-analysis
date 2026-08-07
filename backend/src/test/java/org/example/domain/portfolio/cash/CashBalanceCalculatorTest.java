package org.example.domain.portfolio.cash;

import org.example.domain.model.TradeSide;
import org.example.domain.money.Money;
import org.example.domain.portfolio.PricedTrade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CashBalanceCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    private final CashBalanceCalculator calculator = new CashBalanceCalculator();

    private static PricedTrade trade(TradeSide side, LocalDate date, String qty,
                                     String moneyPerUnit, String commission) {
        return new PricedTrade(side, date, Money.of(qty), Money.of(moneyPerUnit),
                Money.of(commission));
    }

    private static BigDecimal rub(String amount) {
        return Money.of(amount);
    }

    @Test
    void withoutTradesBalanceIsWhatWasDeposited() {
        assertThat(calculator.balance(rub("100000"), List.of(), Money.ZERO))
                .isEqualByComparingTo("100000");
    }

    @Test
    void purchaseTakesBothNotionalAndCommission() {
        PricedTrade buy = trade(TradeSide.BUY, DAY, "10", "1000", "50");

        // 100 000 − 10 000 − 50
        assertThat(calculator.balance(rub("100000"), List.of(buy), Money.ZERO))
                .isEqualByComparingTo("89950");
    }

    @Test
    void saleReturnsMoneyMinusCommission() {
        PricedTrade buy = trade(TradeSide.BUY, DAY, "10", "1000", "50");
        PricedTrade sell = trade(TradeSide.SELL, DAY.plusDays(30), "10", "1200", "60");

        // 100 000 − 10 000 − 50 + 12 000 − 60
        assertThat(calculator.balance(rub("100000"), List.of(buy, sell), Money.ZERO))
                .isEqualByComparingTo("101890");
    }

    @Test
    void receivedPayoutsIncreaseBalance() {
        PricedTrade buy = trade(TradeSide.BUY, DAY, "10", "1000", "0");

        assertThat(calculator.balance(rub("100000"), List.of(buy), rub("3500")))
                .isEqualByComparingTo("93500");
    }

    @Test
    void goesNegativeWhenTradesAreEnteredWithoutAnyDeposit() {
        // Так выглядит счёт, заведённый до появления учёта денег: сделки есть, пополнений нет.
        // Это не ошибка расчёта, и калькулятор не обязан её скрывать.
        PricedTrade buy = trade(TradeSide.BUY, DAY, "10", "1000", "50");

        assertThat(calculator.balance(Money.ZERO, List.of(buy), Money.ZERO)).isNegative();
    }

    @Test
    void withdrawalsReduceBalance() {
        assertThat(calculator.balance(rub("70000"), List.of(), Money.ZERO))
                .isEqualByComparingTo("70000");
    }

    @Test
    void reportedBalanceHidesMinusWhenCashIsNotTracked() {
        // Счёт без единого движения денег: остаток неизвестен, и минус на стоимость покупок
        // нельзя ни показывать как «свободные деньги», ни вычитать из капитала.
        PricedTrade buy = trade(TradeSide.BUY, DAY, "10", "1000", "50");

        assertThat(calculator.reportedBalance(false, Money.ZERO, List.of(buy), Money.ZERO))
                .isEqualByComparingTo("0");
    }

    @Test
    void reportedBalanceIsTheRealOneWhenCashIsTracked() {
        PricedTrade buy = trade(TradeSide.BUY, DAY, "10", "1000", "50");

        assertThat(calculator.reportedBalance(true, rub("100000"), List.of(buy), Money.ZERO))
                .isEqualByComparingTo("89950");
    }

    @Test
    void trackedAccountMayStillGoNegative() {
        // Учёт денег ведётся, но пополнений завели меньше, чем потратили на сделки —
        // это уже расхождение в данных пользователя, и прятать его нельзя.
        PricedTrade buy = trade(TradeSide.BUY, DAY, "10", "1000", "0");

        assertThat(calculator.reportedBalance(true, rub("5000"), List.of(buy), Money.ZERO))
                .isNegative();
    }

    @Test
    void centsSurviveALongChainOfTrades() {
        // Копеечные величины, каждая из которых в double неточна: сто покупок по 0.07 ₽
        // накапливали расхождение, из-за которого остаток не сходился с суммой движений.
        List<PricedTrade> trades = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> trade(TradeSide.BUY, DAY.plusDays(i), "1", "0.07", "0"))
                .toList();

        assertThat(calculator.balance(rub("10"), trades, Money.ZERO))
                .isEqualByComparingTo("3");
    }
}
