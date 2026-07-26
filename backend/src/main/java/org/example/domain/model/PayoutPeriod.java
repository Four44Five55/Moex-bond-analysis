package org.example.domain.model;

/** Периодичность начисления процентов по вкладу. */
public enum PayoutPeriod {

    MONTHLY(1),
    QUARTERLY(3),
    SEMIANNUAL(6),
    ANNUAL(12),
    /** Проценты начисляются один раз — в конце срока. */
    AT_MATURITY(0);

    private final int months;

    PayoutPeriod(int months) {
        this.months = months;
    }

    /** Длина периода в месяцах; 0 — единственное начисление в дату закрытия. */
    public int months() {
        return months;
    }
}
