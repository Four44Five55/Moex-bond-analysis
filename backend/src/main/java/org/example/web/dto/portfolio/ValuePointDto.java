package org.example.web.dto.portfolio;

import java.time.LocalDate;

/** Точка графика стоимости портфеля: рыночная стоимость против вложенного капитала. */
public record ValuePointDto(LocalDate date, double value, double invested, double pnl) {
}
