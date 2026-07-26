package org.example.domain.portfolio.allocation;

import org.example.domain.model.Instrument;

/**
 * Измерение аллокации портфеля (Strategy): относит инструмент к категории.
 * Новое измерение = новый класс-бин, ничего существующего менять не нужно (OCP).
 */
public interface AllocationDimension {

    /** Ключ измерения для API (?by=...): "type" | "sector" | "currency". */
    String key();

    /** Категория, к которой относится инструмент в этом измерении. */
    String categoryOf(Instrument instrument);
}
