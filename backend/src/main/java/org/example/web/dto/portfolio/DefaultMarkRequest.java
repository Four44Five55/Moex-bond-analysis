package org.example.web.dto.portfolio;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Отметка «по бумаге выплаты не поступают начиная с даты». */
public record DefaultMarkRequest(
        @NotNull LocalDate paymentsStoppedOn,
        String note
) {
}
