package org.example.domain.model;

import java.time.LocalDate;

/**
 * Купонная выплата по графику: сумма на одну облигацию.
 *
 * @param estimated {@code true} — купон не зафиксирован биржей и посчитан по последней известной
 *                  ставке. Так живут флоатеры: ставка периода привязана к ключевой или RUONIA
 *                  и становится известна лишь при его открытии, поэтому у выпуска с 55 купонами
 *                  в графике MOEX сумма может стоять всего у шести.
 */
public record CouponPayment(LocalDate date, double perBond, boolean estimated) {

    /** Купон, объявленный биржей: сумма известна точно. */
    public CouponPayment(LocalDate date, double perBond) {
        this(date, perBond, false);
    }
}
