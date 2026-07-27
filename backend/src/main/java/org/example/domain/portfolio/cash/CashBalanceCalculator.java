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

    /**
     * Остаток для сумм капитала: если по счёту не заведено ни одного движения денег, учёт денег
     * по нему не ведётся и остаток <b>неизвестен</b> — тогда ноль, а не минус на всю стоимость
     * покупок.
     * <p>
     * Без этого счёт, заведённый до появления учёта денег, показывал бы «свободные деньги»
     * в размере минус всех покупок, а «всего капитала» — на ту же сумму меньше правды.
     * То же правило действует для XIRR ({@code PortfolioService.capitalFlows}): у счёта без
     * движений денег потоки строятся по сделкам, и если добавить туда ещё и отрицательный
     * остаток завершающим потоком, покупки вычтутся дважды.
     *
     * @param cashTracked ведётся ли по счёту учёт денег — то есть заведено ли хоть одно движение
     */
    public double reportedBalance(boolean cashTracked, double externalNet,
                                  List<PricedTrade> trades, double receivedIncome) {
        return cashTracked ? balance(externalNet, trades, receivedIncome) : 0.0;
    }
}
