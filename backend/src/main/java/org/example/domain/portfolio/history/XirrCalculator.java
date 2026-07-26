package org.example.domain.portfolio.history;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Внутренняя норма доходности для потоков с произвольными датами (XIRR) — годовая ставка r,
 * при которой чистая приведённая стоимость потоков равна нулю.
 * <p>
 * Основной метод — Ньютона (быстрая сходимость), но на реальных портфелях он расходится:
 * производная NPV обращается почти в ноль, а шаг выбрасывает ставку за область определения
 * (r ≤ −1). Поэтому при неудаче применяется бисекция — медленнее, но сходится гарантированно,
 * если корень существует. Расчёт чистый: без БД и сети.
 */
@Component
public class XirrCalculator {

    private static final double DAYS_PER_YEAR = 365.0;
    /**
     * Ставка не может достигать −100%: знаменатель (1+r)^t обращается в ноль.
     * Граница взята близко к −1, чтобы почти полная потеря капитала всё же давала число:
     * например, −100 000 → +1 за год — это −99.9990% годовых.
     */
    private static final double MIN_RATE = -0.999999;
    private static final double MAX_RATE = 1_000.0;
    private static final double NPV_TOLERANCE = 1e-7;
    private static final double RATE_TOLERANCE = 1e-9;
    private static final int NEWTON_ITERATIONS = 60;
    private static final int BISECTION_ITERATIONS = 200;
    private static final double INITIAL_GUESS = 0.1;

    /**
     * @return годовая доходность долей единицы (0.15 = 15%), либо {@code null},
     *         если потоки не задают решения: пусто, все одного знака или корень не найден.
     */
    public Double compute(List<CashFlow> flows) {
        if (!hasBothSigns(flows)) {
            return null;
        }
        LocalDate start = flows.stream()
                .map(CashFlow::date)
                .min(LocalDate::compareTo)
                .orElseThrow();

        Double newton = newton(flows, start);
        return newton != null ? newton : bisection(flows, start);
    }

    /** Потоки без вложений или без поступлений корня не имеют. */
    private boolean hasBothSigns(List<CashFlow> flows) {
        if (flows == null || flows.size() < 2) {
            return false;
        }
        boolean positive = false;
        boolean negative = false;
        for (CashFlow f : flows) {
            positive |= f.amount() > 0;
            negative |= f.amount() < 0;
        }
        return positive && negative;
    }

    private Double newton(List<CashFlow> flows, LocalDate start) {
        double rate = INITIAL_GUESS;
        for (int i = 0; i < NEWTON_ITERATIONS; i++) {
            double npv = npv(flows, start, rate);
            if (Math.abs(npv) < NPV_TOLERANCE) {
                return rate;
            }
            double derivative = npvDerivative(flows, start, rate);
            if (Math.abs(derivative) < 1e-12) {
                return null; // плоский участок — шаг Ньютона не определён
            }
            double next = rate - npv / derivative;
            if (!Double.isFinite(next) || next <= MIN_RATE || next > MAX_RATE) {
                return null; // вышли за область определения — добиваем бисекцией
            }
            if (Math.abs(next - rate) < RATE_TOLERANCE) {
                return next;
            }
            rate = next;
        }
        return null;
    }

    /** Половинное деление на отрезке, где NPV меняет знак. Границу ищем расширением вправо. */
    private Double bisection(List<CashFlow> flows, LocalDate start) {
        double low = MIN_RATE;
        double lowNpv = npv(flows, start, low);
        double high = INITIAL_GUESS;
        double highNpv = npv(flows, start, high);

        while (Math.signum(lowNpv) == Math.signum(highNpv) && high < MAX_RATE) {
            high = high * 2 + 1;
            highNpv = npv(flows, start, high);
        }
        if (Math.signum(lowNpv) == Math.signum(highNpv)) {
            return null; // корня на допустимом интервале нет
        }

        for (int i = 0; i < BISECTION_ITERATIONS; i++) {
            double mid = (low + high) / 2;
            double midNpv = npv(flows, start, mid);
            if (Math.abs(midNpv) < NPV_TOLERANCE || (high - low) < RATE_TOLERANCE) {
                return mid;
            }
            if (Math.signum(midNpv) == Math.signum(lowNpv)) {
                low = mid;
                lowNpv = midNpv;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    /** NPV = Σ amount / (1+r)^(дней от старта / 365). */
    private double npv(List<CashFlow> flows, LocalDate start, double rate) {
        double sum = 0;
        for (CashFlow f : flows) {
            sum += f.amount() / Math.pow(1 + rate, years(start, f.date()));
        }
        return sum;
    }

    /** d(NPV)/dr = Σ −t · amount / (1+r)^(t+1). */
    private double npvDerivative(List<CashFlow> flows, LocalDate start, double rate) {
        double sum = 0;
        for (CashFlow f : flows) {
            double t = years(start, f.date());
            sum -= t * f.amount() / Math.pow(1 + rate, t + 1);
        }
        return sum;
    }

    private double years(LocalDate start, LocalDate date) {
        return ChronoUnit.DAYS.between(start, date) / DAYS_PER_YEAR;
    }
}
