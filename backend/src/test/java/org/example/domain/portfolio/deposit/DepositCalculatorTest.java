package org.example.domain.portfolio.deposit;

import org.example.domain.model.Deposit;
import org.example.domain.model.PayoutPeriod;
import org.example.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DepositCalculatorTest {

    private static final LocalDate OPEN = LocalDate.of(2026, 1, 1);

    /** Копейка — там, где сумма зависит от разной длины календарных периодов. */
    private static final BigDecimal KOPECK = Money.of("0.01");

    private final DepositCalculator calculator = new DepositCalculator();

    private Deposit deposit(String principal, String rate, LocalDate matures,
                            boolean capitalized, PayoutPeriod period) {
        Deposit d = new Deposit();
        d.setName("Вклад");
        d.setPrincipal(Money.of(principal));
        d.setRatePct(Money.of(rate));
        d.setOpenedOn(OPEN);
        d.setMaturesOn(matures);
        d.setCapitalized(capitalized);
        d.setPayoutPeriod(period);
        return d;
    }

    private static BigDecimal sum(List<InterestAccrual> accruals) {
        return accruals.stream().map(InterestAccrual::amount).reduce(Money.ZERO, BigDecimal::add);
    }

    @Test
    void atMaturityAccruesOnceForTheWholeTerm() {
        Deposit d = deposit("1000000", "20", OPEN.plusDays(365), false, PayoutPeriod.AT_MATURITY);

        List<InterestAccrual> schedule = calculator.schedule(d);

        assertThat(schedule).hasSize(1);
        assertThat(schedule.get(0).date()).isEqualTo(OPEN.plusDays(365));
        // ACT/365 за ровно 365 дней даёт годовую ставку без остатка
        assertThat(schedule.get(0).amount()).isEqualByComparingTo("200000");
    }

    @Test
    void quarterlyPayoutsDoNotGrowTheBase() {
        Deposit d = deposit("1000000", "20", OPEN.plusMonths(12), false, PayoutPeriod.QUARTERLY);

        List<InterestAccrual> schedule = calculator.schedule(d);

        assertThat(schedule).hasSize(4);
        assertThat(schedule).allSatisfy(a ->
                assertThat(a.base()).isEqualByComparingTo("1000000"));
        // Год из четырёх кварталов по ACT/365 даёт ровно годовую ставку
        assertThat(sum(schedule)).isCloseTo(Money.of("200000"), within(KOPECK));
    }

    @Test
    void capitalizationGrowsTheBaseAndBeatsSimpleInterest() {
        Deposit simple = deposit("1000000", "20", OPEN.plusMonths(12), false, PayoutPeriod.QUARTERLY);
        Deposit compound = deposit("1000000", "20", OPEN.plusMonths(12), true, PayoutPeriod.QUARTERLY);

        BigDecimal simpleInterest = calculator.totalInterest(simple);
        BigDecimal compoundInterest = calculator.totalInterest(compound);

        assertThat(compoundInterest).isGreaterThan(simpleInterest);
        // 1 000 000 × (1 + 20%/4)^4 − 1 000 000 ≈ 215 506, с поправкой на разную длину кварталов
        assertThat(compoundInterest).isCloseTo(Money.of("215506"), within(Money.of("500")));
        assertThat(calculator.schedule(compound).get(1).base())
                .isGreaterThan(Money.of("1000000"));
    }

    @Test
    void valueIncludesInterestEarnedButNotYetPaid() {
        Deposit d = deposit("1000000", "20", OPEN.plusDays(365), false, PayoutPeriod.AT_MATURITY);

        // Полгода прошло — половина процентов заработана, но ещё не выплачена
        BigDecimal value = calculator.valueOn(d, OPEN.plusDays(182));

        assertThat(value).isCloseTo(Money.of("1099726"), within(Money.of("50")));
    }

    @Test
    void afterMaturityPaidOutInterestIsNotPartOfValue() {
        Deposit paying = deposit("1000000", "20", OPEN.plusMonths(12), false, PayoutPeriod.QUARTERLY);
        Deposit compound = deposit("1000000", "20", OPEN.plusMonths(12), true, PayoutPeriod.QUARTERLY);
        LocalDate afterEnd = OPEN.plusMonths(14);

        // Проценты уже ушли владельцу — во вкладе осталось только тело
        assertThat(calculator.valueOn(paying, afterEnd)).isEqualByComparingTo("1000000");
        // А капитализированные проценты остались во вкладе
        assertThat(calculator.valueOn(compound, afterEnd)).isCloseTo(
                Money.of("1000000").add(calculator.totalInterest(compound)), within(KOPECK));
    }

    @Test
    void valueIsZeroBeforeTheDepositIsOpened() {
        Deposit d = deposit("500000", "15", OPEN.plusMonths(6), false, PayoutPeriod.MONTHLY);

        assertThat(calculator.valueOn(d, OPEN.minusDays(1))).isEqualByComparingTo("0");
    }

    @Test
    void capitalizedDepositPaysOutOnceAtMaturity() {
        Deposit d = deposit("1000000", "20", OPEN.plusMonths(12), true, PayoutPeriod.QUARTERLY);

        List<InterestAccrual> payouts = calculator.payouts(d);

        assertThat(payouts).hasSize(1);
        assertThat(payouts.get(0).date()).isEqualTo(OPEN.plusMonths(12));
        assertThat(payouts.get(0).amount()).isEqualByComparingTo(calculator.totalInterest(d));
    }

    @Test
    void interestReceivedIsCountedOnlyInsideThePeriod() {
        Deposit d = deposit("1000000", "20", OPEN.plusMonths(12), false, PayoutPeriod.QUARTERLY);

        BigDecimal firstHalf = calculator
                .interestReceivedBetween(d, OPEN.minusDays(1), OPEN.plusMonths(6));
        BigDecimal whole = calculator
                .interestReceivedBetween(d, OPEN.minusDays(1), OPEN.plusMonths(12));

        assertThat(firstHalf).isLessThan(whole);
        assertThat(whole).isEqualByComparingTo(calculator.totalInterest(d));
    }

    @Test
    void degenerateTermProducesNoAccruals() {
        Deposit d = deposit("1000000", "20", OPEN, false, PayoutPeriod.MONTHLY);

        assertThat(calculator.schedule(d)).isEmpty();
        assertThat(calculator.totalInterest(d)).isEqualByComparingTo("0");
    }
}
