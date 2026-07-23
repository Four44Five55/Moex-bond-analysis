package org.example.domain.model;

import java.time.LocalDate;

/** Амортизационная выплата (частичное погашение номинала) на одну облигацию. */
public record AmortizationPayment(LocalDate date, double perBond) {
}
