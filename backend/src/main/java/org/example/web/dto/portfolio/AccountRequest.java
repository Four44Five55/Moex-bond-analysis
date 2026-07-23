package org.example.web.dto.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.example.domain.model.AccountType;
import org.example.domain.model.IisType;

/** Создание/обновление счёта. iisType обязателен только для type = IIS. */
public record AccountRequest(
        @NotBlank String name,
        @NotNull AccountType type,
        IisType iisType,
        @PositiveOrZero double defaultCommissionPct,
        @PositiveOrZero double baseTaxRate
) {
}
