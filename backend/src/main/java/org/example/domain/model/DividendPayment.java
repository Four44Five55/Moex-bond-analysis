package org.example.domain.model;

import java.time.LocalDate;

/** Дивиденд: сумма на одну акцию с датой закрытия реестра. */
public record DividendPayment(LocalDate recordDate, double perShare, String currency) {
}
