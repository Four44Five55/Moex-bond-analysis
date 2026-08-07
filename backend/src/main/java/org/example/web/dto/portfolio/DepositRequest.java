package org.example.web.dto.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.example.domain.model.PayoutPeriod;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Заведение или правка вклада. */
public record DepositRequest(
        @NotBlank String name,
        @NotNull @Positive BigDecimal principal,
        @NotNull @PositiveOrZero BigDecimal ratePct,
        @NotNull LocalDate openedOn,
        @NotNull LocalDate maturesOn,
        boolean capitalized,
        @NotNull PayoutPeriod payoutPeriod,
        String note,
        /** Счёт, с которого сняты деньги; null — вклад открыт деньгами со стороны. */
        Long sourceAccountId
) {
}
