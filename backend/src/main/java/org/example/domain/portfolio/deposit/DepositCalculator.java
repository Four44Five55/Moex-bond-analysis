package org.example.domain.portfolio.deposit;

import org.example.domain.model.Deposit;
import org.example.domain.money.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    private static final BigDecimal DAYS_PER_YEAR = Money.of("365");

    /**
     * Делитель формулы целиком: 100 (проценты) × 365 (дней в году). Одно деление на всю формулу
     * вместо двух — меньше округлений в цепочке, а у капитализируемого вклада результат
     * периода становится базой следующего, и ошибка иначе накапливалась бы от периода к периоду.
     */
    private static final BigDecimal RATE_DIVISOR = Money.HUNDRED.multiply(DAYS_PER_YEAR);

    /** Все начисления процентов за срок вклада, по возрастанию даты. */
    public List<InterestAccrual> schedule(Deposit deposit) {
        LocalDate maturity = deposit.getMaturesOn();
        LocalDate periodStart = deposit.getOpenedOn();
        if (!maturity.isAfter(periodStart)) {
            return List.of();
        }

        List<InterestAccrual> accruals = new ArrayList<>();
        BigDecimal base = deposit.getPrincipal();
        int step = deposit.getPayoutPeriod().months();

        while (periodStart.isBefore(maturity)) {
            LocalDate periodEnd = step == 0 ? maturity : periodStart.plusMonths(step);
            if (periodEnd.isAfter(maturity)) {
                periodEnd = maturity;
            }
            BigDecimal amount = interest(base, deposit.getRatePct(), periodStart, periodEnd);
            accruals.add(new InterestAccrual(periodEnd, amount, base));
            if (deposit.isCapitalized()) {
                base = base.add(amount);
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
        BigDecimal total = sum(schedule);
        return List.of(new InterestAccrual(deposit.getMaturesOn(), total, deposit.getPrincipal()));
    }

    /**
     * Стоимость вклада на дату: тело плюс проценты, которые уже начислены, но ещё не выплачены
     * (у капитализируемого — плюс всё, что причислено ранее). Аналог НКД у облигации.
     *
     * @return 0 до даты открытия; после закрытия — стоимость на дату закрытия
     */
    public BigDecimal valueOn(Deposit deposit, LocalDate date) {
        if (date.isBefore(deposit.getOpenedOn())) {
            return Money.ZERO;
        }
        LocalDate effective = date.isAfter(deposit.getMaturesOn()) ? deposit.getMaturesOn() : date;

        BigDecimal base = deposit.getPrincipal();
        LocalDate lastClosed = deposit.getOpenedOn();
        for (InterestAccrual accrual : schedule(deposit)) {
            if (accrual.date().isAfter(effective)) {
                break;
            }
            if (deposit.isCapitalized()) {
                base = base.add(accrual.amount());
            }
            lastClosed = accrual.date();
        }
        return base.add(interest(base, deposit.getRatePct(), lastClosed, effective));
    }

    /** Проценты за весь срок вклада. */
    public BigDecimal totalInterest(Deposit deposit) {
        return sum(schedule(deposit));
    }

    /** Проценты, которые владелец фактически получит в промежутке (from, to]. */
    public BigDecimal interestReceivedBetween(Deposit deposit, LocalDate from, LocalDate to) {
        return payouts(deposit).stream()
                .filter(a -> a.date().isAfter(from) && !a.date().isAfter(to))
                .map(InterestAccrual::amount)
                .reduce(Money.ZERO, BigDecimal::add);
    }

    private static BigDecimal sum(List<InterestAccrual> accruals) {
        return accruals.stream().map(InterestAccrual::amount).reduce(Money.ZERO, BigDecimal::add);
    }

    private BigDecimal interest(BigDecimal base, BigDecimal ratePct, LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 0) {
            return Money.ZERO;
        }
        return base.multiply(ratePct)
                .multiply(Money.of(days))
                .divide(RATE_DIVISOR, Money.CALC_SCALE, Money.ROUNDING);
    }
}
