package org.example.domain.model;

/** Направление движения денег по счёту. */
public enum CashDirection {
    /** Пополнение: деньги пришли на счёт извне. */
    IN,
    /** Вывод: деньги ушли со счёта наружу. */
    OUT
}
