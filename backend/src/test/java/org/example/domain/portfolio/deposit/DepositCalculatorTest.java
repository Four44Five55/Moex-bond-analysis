package org.example.domain.portfolio.deposit;

import org.example.domain.model.Deposit;
import org.example.domain.model.PayoutPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DepositCalculatorTest {

    private static final LocalDate OPEN = LocalDate.of(2026, 1, 1);
    private final DepositCalculator calculator = new DepositCalculator();

    private Deposit deposit(double principal, double rate, LocalDate matures,
                            boolean capitalized, PayoutPeriod period) {
        Deposit d = new Deposit();
        d.setName("Вклад");
        d.setPrincipal(principal);
        d.setRatePct(rate);
        d.setOpenedOn(OPEN);
        d.setMaturesOn(matures);
        d.setCapitalized(capitalized);
        d.setPayoutPeriod(period);
        return d;
    }

    @Test
    void atMaturityAccruesOnceForTheWholeTerm() {
        Deposit d = deposit(1_000_000, 20, OPEN.plusDays(365), false, PayoutPeriod.AT_MATURITY);

        List<InterestAccrual> schedule = calculator.schedule(d);

        assertThat(schedule).hasSize(1);
        assertThat(schedule.get(0).date()).isEqualTo(OPEN.plusDays(365));
        assertThat(schedule.get(0).amount()).isCloseTo(200_000, within(0.01));
    }

    @Test
    void quarterlyPayoutsDoNotGrowTheBase() {
        Deposit d = deposit(1_000_000, 20, OPEN.plusMonths(12), false, PayoutPeriod.QUARTERLY);

        List<InterestAccrual> schedule = calculator.schedule(d);

        assertThat(schedule).hasSize(4);
        assertThat(schedule).allSatisfy(a -> assertThat(a.base()).isEqualTo(1_000_000));
        // Год из четырёх кварталов по ACT/365 даёт ровно годовую ставку
        assertThat(schedule.stream().mapToDouble(InterestAccrual::amount).sum())
                .isCloseTo(200_000, within(0.01));
    }

    @Test
    void capitalizationGrowsTheBaseAndBeatsSimpleInterest() {
        Deposit simple = deposit(1_000_000, 20, OPEN.plusMonths(12), false, PayoutPeriod.QUARTERLY);
        Deposit compound = deposit(1_000_000, 20, OPEN.plusMonths(12), true, PayoutPeriod.QUARTERLY);

        double simpleInterest = calculator.totalInterest(simple);
        double compoundInterest = calculator.totalInterest(compound);

        assertThat(compoundInterest).isGreaterThan(simpleInterest);
        // 1 000 000 × (1 + 20%/4)^4 − 1 000 000 ≈ 215 506, с поправкой на разную длину кварталов
        assertThat(compoundInterest).isCloseTo(215_506, within(500.0));
        assertThat(calculator.schedule(compound).get(1).base()).isGreaterThan(1_000_000);
    }

    @Test
    void valueIncludesInterestEarnedButNotYetPaid() {
        Deposit d = deposit(1_000_000, 20, OPEN.plusDays(365), false, PayoutPeriod.AT_MATURITY);

        // Полгода прошло — половина процентов заработана, но ещё не выплачена
        double value = calculator.valueOn(d, OPEN.plusDays(182));

        assertThat(value).isCloseTo(1_000_000 + 99_726, within(50.0));
    }

    @Test
    void afterMaturityPaidOutInterestIsNotPartOfValue() {
        Deposit paying = deposit(1_000_000, 20, OPEN.plusMonths(12), false, PayoutPeriod.QUARTERLY);
        Deposit compound = deposit(1_000_000, 20, OPEN.plusMonths(12), true, PayoutPeriod.QUARTERLY);
        LocalDate afterEnd = OPEN.plusMonths(14);

        // Проценты уже ушли владельцу — во вкладе осталось только тело
        assertThat(calculator.valueOn(paying, afterEnd)).isCloseTo(1_000_000, within(0.01));
        // А капитализированные проценты остались во вкладе
        assertThat(calculator.valueOn(compound, afterEnd))
                .isCloseTo(1_000_000 + calculator.totalInterest(compound), within(0.01));
    }

    @Test
    void valueIsZeroBeforeTheDepositIsOpened() {
        Deposit d = deposit(500_000, 15, OPEN.plusMonths(6), false, PayoutPeriod.MONTHLY);

        assertThat(calculator.valueOn(d, OPEN.minusDays(1))).isZero();
    }

    @Test
    void capitalizedDepositPaysOutOnceAtMaturity() {
        Deposit d = deposit(1_000_000, 20, OPEN.plusMonths(12), true, PayoutPeriod.QUARTERLY);

        List<InterestAccrual> payouts = calculator.payouts(d);

        assertThat(payouts).hasSize(1);
        assertThat(payouts.get(0).date()).isEqualTo(OPEN.plusMonths(12));
        assertThat(payouts.get(0).amount()).isCloseTo(calculator.totalInterest(d), within(0.01));
    }

    @Test
    void interestReceivedIsCountedOnlyInsideThePeriod() {
        Deposit d = deposit(1_000_000, 20, OPEN.plusMonths(12), false, PayoutPeriod.QUARTERLY);

        double firstHalf = calculator.interestReceivedBetween(d, OPEN.minusDays(1), OPEN.plusMonths(6));
        double whole = calculator.interestReceivedBetween(d, OPEN.minusDays(1), OPEN.plusMonths(12));

        assertThat(firstHalf).isLessThan(whole);
        assertThat(whole).isCloseTo(calculator.totalInterest(d), within(0.01));
    }

    @Test
    void degenerateTermProducesNoAccruals() {
        Deposit d = deposit(1_000_000, 20, OPEN, false, PayoutPeriod.MONTHLY);

        assertThat(calculator.schedule(d)).isEmpty();
        assertThat(calculator.totalInterest(d)).isZero();
    }
}
