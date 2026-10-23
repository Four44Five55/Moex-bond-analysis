package org.example.domain.portfolio.defaults;

import org.example.domain.model.AmortizationPayment;
import org.example.domain.model.CouponPayment;
import org.example.domain.model.TradeSide;
import org.example.domain.money.Money;
import org.example.domain.portfolio.PricedTrade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Итог вложения в бумагу в дефолте ({@link DefaultLoss}) по истории сделок одного счёта.
 * <p>
 * Платёж графика разносится по одной из трёх корзин в зависимости от даты:
 * <ul>
 *   <li>раньше даты дефолта и раньше сегодня — получен;</li>
 *   <li>с даты дефолта и до сегодня — просрочен эмитентом;</li>
 *   <li>с даты дефолта и с сегодня — ещё не наступил, но рассчитывать на него не стоит.</li>
 * </ul>
 * Получено и просрочено считаются от количества на дату платежа — как и в портфеле, где выплата
 * засчитывается, только если позиция на дату отсечки была. Будущее — от текущего остатка.
 */
@Component
public class DefaultLossCalculator {

    /**
     * @param trades          сделки счёта по бумаге, приведённые к деньгам
     * @param recovered       возмещение, полученное после дефолта, — уже суммой
     * @param cleanPricePerUnit текущая цена одной бумаги без НКД
     * @param cutoff          с этой даты эмитент не платит
     */
    public DefaultLoss compute(List<PricedTrade> trades, List<CouponPayment> coupons,
                               List<AmortizationPayment> amortizations, BigDecimal recovered,
                               BigDecimal cleanPricePerUnit, LocalDate cutoff, LocalDate today) {
        BigDecimal invested = Money.ZERO;
        BigDecimal proceeds = Money.ZERO;
        for (PricedTrade t : trades) {
            if (t.side() == TradeSide.BUY) {
                invested = invested.add(t.gross()).add(Money.orZero(t.commission()));
            } else {
                proceeds = proceeds.add(t.gross()).subtract(Money.orZero(t.commission()));
            }
        }
        BigDecimal quantity = quantityAsOf(trades, today);

        Buckets couponBuckets = new Buckets();
        for (CouponPayment c : coupons) {
            couponBuckets.add(c.date(), Money.of(c.perBond()), trades, quantity, cutoff, today);
        }
        Buckets principalBuckets = new Buckets();
        for (AmortizationPayment a : amortizations) {
            principalBuckets.add(a.date(), Money.of(a.perBond()), trades, quantity, cutoff, today);
        }

        return new DefaultLoss(quantity, invested, proceeds,
                couponBuckets.received, principalBuckets.received, Money.orZero(recovered),
                Money.atLeastZero(quantity).multiply(Money.orZero(cleanPricePerUnit)),
                couponBuckets.missed, principalBuckets.missed,
                couponBuckets.future, principalBuckets.future);
    }

    /** Платежи графика, разнесённые по корзинам «получено / просрочено / впереди». */
    private static final class Buckets {
        private BigDecimal received = Money.ZERO;
        private BigDecimal missed = Money.ZERO;
        private BigDecimal future = Money.ZERO;

        private void add(LocalDate date, BigDecimal perUnit, List<PricedTrade> trades,
                         BigDecimal currentQuantity, LocalDate cutoff, LocalDate today) {
            if (date == null) {
                return;
            }
            boolean past = date.isBefore(today);
            boolean paid = cutoff == null || date.isBefore(cutoff);
            if (past) {
                BigDecimal qty = quantityAsOf(trades, date);
                if (!Money.isPositive(qty)) {
                    return;
                }
                if (paid) {
                    received = received.add(perUnit.multiply(qty));
                } else {
                    missed = missed.add(perUnit.multiply(qty));
                }
            } else if (!paid && Money.isPositive(currentQuantity)) {
                future = future.add(perUnit.multiply(currentQuantity));
            }
        }
    }

    /** Количество на конец дня {@code date}: покупки минус продажи с датой не позже неё. */
    private static BigDecimal quantityAsOf(List<PricedTrade> trades, LocalDate date) {
        BigDecimal qty = Money.ZERO;
        for (PricedTrade t : trades) {
            if (!t.date().isAfter(date)) {
                qty = t.side() == TradeSide.BUY ? qty.add(t.quantity()) : qty.subtract(t.quantity());
            }
        }
        return qty;
    }
}
