package org.example.application.portfolio;

import org.example.domain.model.Bond;
import org.example.domain.model.Instrument;
import org.example.domain.money.Money;

import java.math.BigDecimal;

/**
 * Приведение цен к деньгам на одну бумагу (₽). Для облигаций цена — % от номинала,
 * поэтому деньги = номинал × цена/100 (+ НКД для сделки); для акций — цена как есть.
 * <p>
 * Это граница между рыночными данными и учётом: на вход приходит котировка ({@code double} —
 * величина по своей природе приблизительная), на выходе всегда {@link BigDecimal}, потому что
 * дальше результат складывается в стоимость позиции, себестоимость и налоговую базу.
 * Обратный переход — в {@code Money.toDouble}, и только там, где начинается аналитика
 * (ряд стоимости, XIRR).
 */
public final class InstrumentPricing {

    private InstrumentPricing() {
    }

    /** Стоимость одной бумаги при сделке (без комиссии), с учётом НКД для облигаций. */
    public static BigDecimal tradeMoneyPerUnit(Instrument instrument, BigDecimal price, BigDecimal aci) {
        if (instrument instanceof Bond bond) {
            return faceShare(bond, price).add(Money.orZero(aci));
        }
        return Money.orZero(price);
    }

    /** Текущая стоимость одной бумаги по последней цене MOEX (чистая, без НКД). */
    public static BigDecimal currentMoneyPerUnit(Instrument instrument) {
        if (instrument == null || instrument.getLastPrice() == null) {
            return Money.ZERO;
        }
        return moneyPerUnit(instrument, Money.of(instrument.getLastPrice()));
    }

    /**
     * Текущая стоимость одной бумаги в позиции — «грязная» цена: чистая плюс НКД.
     * <p>
     * Себестоимость позиции считается по {@link #tradeMoneyPerUnit}, а он НКД включает:
     * покупатель облигации платит продавцу накопленный купон. Оценивать ту же позицию по чистой
     * цене — значит показывать убыток ровно в размере уплаченного НКД сразу после покупки
     * и держать его до выплаты купона. Обе стороны сравнения должны быть грязными.
     * <p>
     * У акций НКД нет, для них это та же цена.
     */
    public static BigDecimal currentPositionMoneyPerUnit(Instrument instrument) {
        BigDecimal clean = currentMoneyPerUnit(instrument);
        if (Money.isPositive(clean) && instrument instanceof Bond bond) {
            return clean.add(Money.of(bond.getAccruedInterest()));
        }
        return clean;
    }

    /**
     * Стоимость одной бумаги по произвольной котировке (например, цене закрытия свечи).
     * Котировки облигаций MOEX выражены в процентах от номинала — без этого перевода
     * облигационная часть портфеля усохнет в номинал раз.
     */
    public static BigDecimal moneyPerUnit(Instrument instrument, BigDecimal price) {
        if (instrument instanceof Bond bond) {
            return faceShare(bond, price);
        }
        return Money.orZero(price);
    }

    /** Доля номинала, соответствующая цене в процентах. */
    private static BigDecimal faceShare(Bond bond, BigDecimal pricePercent) {
        return Money.of(bond.getFaceValue())
                .multiply(Money.orZero(pricePercent))
                .divide(Money.HUNDRED, Money.CALC_SCALE, Money.ROUNDING);
    }
}
