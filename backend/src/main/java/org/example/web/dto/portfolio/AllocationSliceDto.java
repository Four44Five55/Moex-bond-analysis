package org.example.web.dto.portfolio;

import java.math.BigDecimal;

/** Доля портфеля в категории (тип/сектор/валюта) для диаграммы аллокации. */
public record AllocationSliceDto(String category, BigDecimal value, double percent) {
}
