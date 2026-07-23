package org.example.domain.model;

/** Тип инвестиционного счёта. */
public enum AccountType {
    /** Обычный брокерский счёт (действует ЛДВ). */
    BROKERAGE,
    /** Индивидуальный инвестиционный счёт (тип уточняется в {@link IisType}). */
    IIS
}
