package org.example.web.dto.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.example.domain.model.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Добавление сделки.
 * <p>
 * {@code @NotNull} у обязательных сумм стоит рядом с {@code @Positive} не для симметрии:
 * {@code @Positive} пропускает {@code null}, и без него пропущенное в JSON количество дошло бы
 * до NOT NULL-колонки и вернулось пользователю как 500 вместо внятного 400. С примитивами
 * этой дыры не было — там Jackson подставлял 0, который {@code @Positive} отклонял.
 */
public record AddTransactionRequest(
        @NotBlank String secId,
        @NotNull TradeSide side,
        @NotNull LocalDate tradeDate,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @PositiveOrZero BigDecimal price,
        /** Не обязателен: у акций НКД нет, у облигаций опущенный означает ноль. */
        @PositiveOrZero BigDecimal aci,
        /** null — рассчитать из % комиссии счёта. */
        BigDecimal commission,
        String note
) {
}
