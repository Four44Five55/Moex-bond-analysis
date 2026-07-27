package org.example.domain.analysis;

import org.example.domain.model.CouponPayment;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Достраивание графика купонов: незафиксированные купоны оцениваются по последней известной ставке.
 * Расчёт чистый: без БД и сети.
 * <p>
 * У облигаций с плавающим купоном (КС, RUONIA) биржа знает сумму только текущего и прошедших
 * периодов, остальные приходят пустыми. Выбрасывать их, как делалось раньше, нельзя: у флоатера
 * с ежеквартальным купоном тогда не остаётся ни одной будущей выплаты, и он молча исчезает
 * из календаря выплат, прогноза дохода и купонной базы налога.
 * <p>
 * Оценка честная ровно настолько, насколько ставка останется прежней, поэтому такие купоны
 * помечаются {@link CouponPayment#estimated()} и должны показываться как оценка. Это принципиально
 * отличается от отброшенной идеи «прикинуть дивиденд по прошлогоднему»: у флоатера действующая
 * ставка известна и объявлена, неизвестен только её будущий дрейф.
 */
@Component
public class CouponScheduleEstimator {

    private static final double DAYS_PER_YEAR = 365.0;

    /** @return график по возрастанию даты: известные купоны как есть, неизвестные — оценкой. */
    public List<CouponPayment> complete(List<ScheduledCoupon> schedule) {
        List<ScheduledCoupon> sorted = schedule.stream()
                .filter(c -> c.date() != null)
                .sorted(Comparator.comparing(ScheduledCoupon::date))
                .toList();

        Double annualRate = impliedAnnualRate(sorted);
        Double lastKnownValue = lastKnownValue(sorted);

        List<CouponPayment> result = new ArrayList<>();
        for (ScheduledCoupon c : sorted) {
            if (isKnown(c)) {
                result.add(new CouponPayment(c.date(), c.value(), false));
                continue;
            }
            Double estimate = estimate(c, annualRate, lastKnownValue);
            if (estimate != null && estimate > 0) {
                result.add(new CouponPayment(c.date(), estimate, true));
            }
        }
        return result;
    }

    private boolean isKnown(ScheduledCoupon c) {
        return c.value() != null && c.value() > 0;
    }

    /**
     * Ставка последнего известного купона в процентах годовых: сумма к номиналу, приведённая
     * к году длиной периода. Берётся именно последний известный — он ближе всего к будущим.
     */
    private Double impliedAnnualRate(List<ScheduledCoupon> sorted) {
        for (int i = sorted.size() - 1; i >= 0; i--) {
            ScheduledCoupon c = sorted.get(i);
            Long days = periodDays(c);
            if (isKnown(c) && days != null && c.faceValue() > 0) {
                return c.value() / c.faceValue() * DAYS_PER_YEAR / days;
            }
        }
        return null;
    }

    private Double lastKnownValue(List<ScheduledCoupon> sorted) {
        for (int i = sorted.size() - 1; i >= 0; i--) {
            if (isKnown(sorted.get(i))) {
                return sorted.get(i).value();
            }
        }
        return null;
    }

    /**
     * Оценка неизвестного купона: ставка на его собственные номинал и длину периода — так
     * амортизация номинала и разная длина периодов учитываются сами собой. Если длины периода
     * биржа не назвала, остаётся повторить последний известный купон.
     */
    private Double estimate(ScheduledCoupon c, Double annualRate, Double lastKnownValue) {
        Long days = periodDays(c);
        if (annualRate != null && days != null && c.faceValue() > 0) {
            return c.faceValue() * annualRate * days / DAYS_PER_YEAR;
        }
        return lastKnownValue;
    }

    private Long periodDays(ScheduledCoupon c) {
        if (c.periodStart() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(c.periodStart(), c.date());
        return days > 0 ? days : null;
    }
}
