package org.example.domain.portfolio.cash;

import org.example.domain.model.TradeSide;
import org.example.domain.portfolio.PricedTrade;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Свободные деньги на счёте. Остаток не хранится, а выводится из истории:
 * <pre>
 *   остаток = пополнения − выводы − стоимость покупок − комиссии + выручка от продаж + полученные выплаты
 * </pre>
 * Расчёт чистый: без БД и сети.
 * <p>
 * Остаток может уйти в минус — это не ошибка ввода, а нормальная ситуация, когда пользователь
 * завёл сделки, но не завёл пополнения (так выглядят все счета, созданные до появления учёта денег).
 * Решать, что показывать в таком случае, — дело вызывающего, а не калькулятора.
 */
@Component
public class CashBalanceCalculator {

    /**
     * @param externalNet    пополнения минус выводы (сумма движений со знаком)
     * @param trades         сделки счёта, приведённые к деньгам
     * @param receivedIncome фактически полученные купоны, амортизации и дивиденды (gross)
     */
    public double balance(double externalNet, List<PricedTrade> trades, double receivedIncome) {
        double fromTrades = 0;
        for (PricedTrade t : trades) {
            double money = t.quantity() * t.moneyPerUnit();
            fromTrades += (t.side() == TradeSide.BUY ? -money : money) - t.commission();
        }
        return externalNet + fromTrades + receivedIncome;
    }
}
