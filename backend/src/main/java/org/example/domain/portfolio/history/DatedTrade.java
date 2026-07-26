package org.example.domain.portfolio.history;

import org.example.domain.model.TradeSide;

import java.time.LocalDate;

/**
 * Сделка в разрезе бумаги для восстановления портфеля на произвольную дату.
 * В отличие от {@link org.example.domain.portfolio.PricedTrade} несёт secId —
 * ряд стоимости строится сразу по всем бумагам счёта, а не по одной.
 *
 * @param moneyPerUnit стоимость одной бумаги в валюте (для облигаций — уже с НКД)
 */
public record DatedTrade(String secId, TradeSide side, LocalDate date,
                         double quantity, double moneyPerUnit, double commission) {
}
