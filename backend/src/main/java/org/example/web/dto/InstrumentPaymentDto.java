package org.example.web.dto;

import java.time.LocalDate;

/** Одна выплата по инструменту из истории MOEX: купон/амортизация/дивиденд на единицу. */
public record InstrumentPaymentDto(
        LocalDate date,
        String kind,       // COUPON | AMORTIZATION | DIVIDEND
        double perUnit,    // на облигацию (₽) или на акцию (₽)
        String currency
) {
}
