package org.example.domain.portfolio.deposit;

import org.example.domain.model.Deposit;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Проценты и стоимость банковского вклада. Расчёт чистый: без БД и сети.
 * <p>
 * День считается по факту, год — 365 дней (ACT/365): проценты за период =
 * база × ставка × дней / 365. Это обычная для российских банков схема; в високосный год
 * она даёт копеечное расхождение с ACT/366, и на фоне того, что ставка вводится руками,
 * усложнять её смысла нет.
 * <p>
 * Периоды отсчитываются календарными месяцами от даты открытия, последний обрезается
 * датой закрытия. У капитализируемого вклада начисленное увеличивает базу следующего периода,
 * у обычного — уходит владельцу и базу не меняет.
 */
@Component
public class DepositCalculator {

    private static final double DAYS_PER_YEAR = 365.0;

    /** Все начисления процентов за срок вклада, по возрастанию даты. */
    public List<InterestAccrual> schedule(Deposit deposit) {
        LocalDate maturity = deposit.getMaturesOn();
        LocalDate periodStart = deposit.getOpenedOn();
        if (!maturity.isAfter(periodStart)) {
            return List.of();
        }

        List<InterestAccrual> accruals = new ArrayList<>();
        double base = deposit.getPrincipal();
        int step = deposit.getPayoutPeriod().months();

        while (periodStart.isBefore(maturity)) {
            LocalDate periodEnd = step == 0 ? maturity : periodStart.plusMonths(step);
            if (periodEnd.isAfter(maturity)) {
                periodEnd = maturity;
            }
            double amount = interest(base, deposit.getRatePct(), periodStart, periodEnd);
            accruals.add(new InterestAccrual(periodEnd, amount, base));
            if (deposit.isCapitalized()) {
                base += amount;
            }
            periodStart = periodEnd;
        }
        return accruals;
    }

    /**
     * Денежные поступления владельцу. У вклада с выплатой процентов это каждое начисление,
     * у капитализируемого — одна сумма в дату закрытия: до неё деньги остаются во вкладе,
     * и показывать их как доход было бы враньём.
     */
    public List<InterestAccrual> payouts(Deposit deposit) {
        List<InterestAccrual> schedule = schedule(deposit);
        if (!deposit.isCapitalized() || schedule.isEmpty()) {
            return schedule;
        }
        double total = schedule.stream().mapToDouble(InterestAccrual::amount).sum();
        return List.of(new InterestAccrual(deposit.getMaturesOn(), total, deposit.getPrincipal()));
    }

    /**
     * Стоимость вклада на дату: тело плюс проценты, которые уже начислены, но ещё не выплачены
     * (у капитализируемого — плюс всё, что причислено ранее). Аналог НКД у облигации.
     *
     * @return 0 до даты открытия; после закрытия — стоимость на дату закрытия
     */
    public double valueOn(Deposit deposit, LocalDate date) {
        if (date.isBefore(deposit.getOpenedOn())) {
            return 0.0;
        }
        LocalDate effective = date.isAfter(deposit.getMaturesOn()) ? deposit.getMaturesOn() : date;

        double base = deposit.getPrincipal();
        LocalDate lastClosed = deposit.getOpenedOn();
        for (InterestAccrual accrual : schedule(deposit)) {
            if (accrual.date().isAfter(effective)) {
                break;
            }
            if (deposit.isCapitalized()) {
                base += accrual.amount();
            }
            lastClosed = accrual.date();
        }
        return base + interest(base, deposit.getRatePct(), lastClosed, effective);
    }

    /** Проценты за весь срок вклада. */
    public double totalInterest(Deposit deposit) {
        return schedule(deposit).stream().mapToDouble(InterestAccrual::amount).sum();
    }

    /** Проценты, которые владелец фактически получит в промежутке (from, to]. */
    public double interestReceivedBetween(Deposit deposit, LocalDate from, LocalDate to) {
        return payouts(deposit).stream()
                .filter(a -> a.date().isAfter(from) && !a.date().isAfter(to))
                .mapToDouble(InterestAccrual::amount)
                .sum();
    }

    private double interest(double base, double ratePct, LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 0) {
            return 0.0;
        }
        return base * ratePct / 100.0 * days / DAYS_PER_YEAR;
    }
}
