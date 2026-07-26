package org.example.domain.portfolio.allocation;

/** Вход агрегатора аллокации: стоимость позиции, уже отнесённая к категории измерения. */
public record Holding(String category, double value) {
}
