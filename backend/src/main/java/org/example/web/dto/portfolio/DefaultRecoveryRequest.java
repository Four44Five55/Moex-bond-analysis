package org.example.web.dto.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Возмещение по бумаге в дефолте — суммой на всю позицию счёта. */
public record DefaultRecoveryRequest(
        @NotBlank String secId,
        @NotNull LocalDate receivedOn,
        @NotNull @Positive BigDecimal amount,
        String note
) {
}
