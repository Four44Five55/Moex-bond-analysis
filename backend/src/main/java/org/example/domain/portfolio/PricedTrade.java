package org.example.domain.portfolio;

import org.example.domain.model.TradeSide;

import java.time.LocalDate;

/**
 * Сделка, приведённая к деньгам: {@code moneyPerUnit} — стоимость одной бумаги в ₽ без комиссии
 * (для облигаций уже сконвертировано из %+НКД), {@code commission} — комиссия за всю сделку.
 */
public record PricedTrade(
        TradeSide side,
        LocalDate date,
        double quantity,
        double moneyPerUnit,
        double commission
) {
}
