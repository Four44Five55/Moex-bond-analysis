package org.example.web.dto.portfolio;

/** Доля портфеля в категории (тип/сектор/валюта) для диаграммы аллокации. */
public record AllocationSliceDto(String category, double value, double percent) {
}
