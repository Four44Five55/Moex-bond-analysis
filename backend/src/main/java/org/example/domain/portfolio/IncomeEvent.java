package org.example.domain.portfolio;

import java.time.LocalDate;

/**
 * Будущая выплата по позиции. {@code estimate} = true для оценённых дивидендов
 * (у MOEX нет точного прогноза будущих дивидендов).
 */
public record IncomeEvent(
        LocalDate date,
        String secId,
        IncomeKind kind,
        double gross,
        boolean estimate
) {
}
