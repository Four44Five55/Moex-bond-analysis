package org.example.domain.portfolio.allocation;

import java.math.BigDecimal;

/** Вход агрегатора аллокации: стоимость позиции, уже отнесённая к категории измерения. */
public record Holding(String category, BigDecimal value) {
}
