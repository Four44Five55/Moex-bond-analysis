package org.example.web.dto.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.example.domain.model.TradeSide;

import java.time.LocalDate;

/** Добавление сделки. commission = null → рассчитать из % комиссии счёта. */
public record AddTransactionRequest(
        @NotBlank String secId,
        @NotNull TradeSide side,
        @NotNull LocalDate tradeDate,
        @Positive double quantity,
        @PositiveOrZero double price,
        @PositiveOrZero double aci,
        Double commission,
        String note
) {
}
