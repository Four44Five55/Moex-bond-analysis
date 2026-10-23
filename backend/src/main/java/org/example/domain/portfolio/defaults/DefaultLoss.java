package org.example.domain.portfolio.defaults;

import org.example.domain.money.Money;

import java.math.BigDecimal;

/**
 * Итог вложения в бумагу, по которой эмитент перестал платить. Все суммы — на позицию счёта, ₽.
 * <p>
 * Величины делятся на две группы, которые <b>нельзя складывать между собой</b>:
 * <ul>
 *   <li>деньги инвестора — вложено, возвращено, оценка, и из них потеря собственных средств
 *       и чистый результат;</li>
 *   <li>график эмитента — недополученные купоны, просроченный и ещё не наступивший номинал.
 *       Это то, что был должен эмитент, а не то, что потерял инвестор: облигация, купленная
 *       за 40% номинала, при неуплаченном номинале теряет 40, а не 100.</li>
 * </ul>
 *
 * @param quantity          бумаг на счёте сейчас
 * @param invested          вложено: все покупки по этой бумаге с НКД и комиссией
 * @param saleProceeds      выручено от продаж за вычетом комиссии
 * @param couponsReceived   купоны, полученные до дефолта
 * @param principalReceived номинал, возвращённый амортизациями до дефолта
 * @param recovered         возмещение после дефолта (реструктуризация, банкротство)
 * @param marketValue       оценка оставшихся бумаг по рынку, без НКД — после дефолта он не копится
 * @param missedCoupons     купоны, которые по графику уже должны были прийти и не пришли
 * @param missedPrincipal   номинал (амортизация, погашение), просроченный эмитентом
 * @param futureCoupons     купоны, срок которых ещё не наступил, — на них рассчитывать не стоит
 * @param futurePrincipal   номинал, срок которого ещё не наступил
 */
public record DefaultLoss(
        BigDecimal quantity,
        BigDecimal invested,
        BigDecimal saleProceeds,
        BigDecimal couponsReceived,
        BigDecimal principalReceived,
        BigDecimal recovered,
        BigDecimal marketValue,
        BigDecimal missedCoupons,
        BigDecimal missedPrincipal,
        BigDecimal futureCoupons,
        BigDecimal futurePrincipal
) {

    /** Вернулось из вложенного: продажи, амортизации до дефолта и возмещение. Купоны — доход, не возврат. */
    public BigDecimal capitalReturned() {
        return saleProceeds.add(principalReceived).add(recovered);
    }

    /**
     * Потеря собственных средств: вложено минус всё, что из вложенного вернулось или ещё стоит
     * на рынке. Купоны до дефолта сюда не входят: они плата за риск, а не возврат тела.
     * Отрицательной не бывает — если вышли в плюс, потери нет.
     */
    public BigDecimal capitalLoss() {
        return Money.atLeastZero(invested.subtract(capitalReturned()).subtract(marketValue));
    }

    /** Чистый результат вложения с учётом купонов: со знаком, отрицательный — убыток. */
    public BigDecimal netResult() {
        return capitalReturned().add(couponsReceived).add(marketValue).subtract(invested);
    }

    /** Коэффициент возмещения: какая доля вложенного вернулась или ещё стоит на рынке, %. */
    public double recoveryRatePct() {
        return Money.percentOf(capitalReturned().add(marketValue), invested);
    }

    public DefaultLoss plus(DefaultLoss o) {
        return new DefaultLoss(quantity.add(o.quantity), invested.add(o.invested),
                saleProceeds.add(o.saleProceeds), couponsReceived.add(o.couponsReceived),
                principalReceived.add(o.principalReceived), recovered.add(o.recovered),
                marketValue.add(o.marketValue), missedCoupons.add(o.missedCoupons),
                missedPrincipal.add(o.missedPrincipal), futureCoupons.add(o.futureCoupons),
                futurePrincipal.add(o.futurePrincipal));
    }

    public static DefaultLoss zero() {
        BigDecimal z = Money.ZERO;
        return new DefaultLoss(z, z, z, z, z, z, z, z, z, z, z);
    }

    public DefaultLoss rounded() {
        return new DefaultLoss(quantity, Money.round(invested), Money.round(saleProceeds),
                Money.round(couponsReceived), Money.round(principalReceived), Money.round(recovered),
                Money.round(marketValue), Money.round(missedCoupons), Money.round(missedPrincipal),
                Money.round(futureCoupons), Money.round(futurePrincipal));
    }
}
