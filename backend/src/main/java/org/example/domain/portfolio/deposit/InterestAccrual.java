package org.example.domain.portfolio.deposit;

import java.time.LocalDate;

/**
 * Начисление процентов за один период.
 *
 * @param base сумма, на которую начислялись проценты — у капитализируемого вклада она растёт
 *             от периода к периоду, и по ней видно, откуда взялась сумма
 */
public record InterestAccrual(LocalDate date, double amount, double base) {
}
