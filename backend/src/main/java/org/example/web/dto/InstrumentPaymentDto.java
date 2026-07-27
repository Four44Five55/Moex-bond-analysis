package org.example.web.dto;

import java.time.LocalDate;

/**
 * Одна выплата по инструменту из истории MOEX: купон/амортизация/дивиденд на единицу.
 *
 * @param sharePct  доля выплаты в денежной цене бумаги <b>на её собственную дату</b>, %.
 *                  У будущих выплат и при отсутствии свечи — от текущей цены;
 *                  {@code null}, если цены нет вовсе.
 * @param estimated купон флоатера, ставка которого ещё не зафиксирована: сумма посчитана
 *                  по последней известной ставке и может измениться
 */
public record InstrumentPaymentDto(
        LocalDate date,
        String kind,       // COUPON | AMORTIZATION | DIVIDEND
        double perUnit,    // на облигацию (₽) или на акцию (₽)
        String currency,
        Double sharePct,
        boolean estimated
) {
}
