package org.example.domain.model;

import java.time.LocalDate;

/** Купонная выплата по графику: сумма на одну облигацию. */
public record CouponPayment(LocalDate date, double perBond) {
}
