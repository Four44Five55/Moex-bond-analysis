package org.example.web.dto.portfolio;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.domain.model.CashDirection;

import java.time.LocalDate;

/** Пополнение счёта или вывод денег. Сумма всегда положительная — знак задаёт направление. */
public record CashMovementRequest(
        @NotNull CashDirection direction,
        @NotNull LocalDate movedOn,
        @Positive double amount,
        String note
) {
}
