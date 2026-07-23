package org.example.application.portfolio;

import org.example.domain.model.Bond;
import org.example.domain.model.Instrument;

/**
 * Приведение цен к деньгам на одну бумагу (₽). Для облигаций цена — % от номинала,
 * поэтому деньги = номинал × цена/100 (+ НКД для сделки); для акций — цена как есть.
 */
public final class InstrumentPricing {

    private InstrumentPricing() {
    }

    /** Стоимость одной бумаги при сделке (без комиссии), с учётом НКД для облигаций. */
    public static double tradeMoneyPerUnit(Instrument instrument, double price, double aci) {
        if (instrument instanceof Bond bond) {
            return bond.getFaceValue() * price / 100.0 + aci;
        }
        return price;
    }

    /** Текущая стоимость одной бумаги по последней цене MOEX (чистая, без НКД). */
    public static double currentMoneyPerUnit(Instrument instrument) {
        if (instrument == null || instrument.getLastPrice() == null) {
            return 0.0;
        }
        if (instrument instanceof Bond bond) {
            return bond.getFaceValue() * instrument.getLastPrice() / 100.0;
        }
        return instrument.getLastPrice();
    }
}
