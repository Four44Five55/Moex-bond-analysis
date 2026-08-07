package org.example.domain.portfolio.cash;

import org.example.domain.model.TradeSide;
import org.example.domain.money.Money;
import org.example.domain.portfolio.PricedTrade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    public BigDecimal balance(BigDecimal externalNet, List<PricedTrade> trades,
                              BigDecimal receivedIncome) {
        BigDecimal fromTrades = Money.ZERO;
        for (PricedTrade t : trades) {
            BigDecimal money = t.gross();
            BigDecimal signed = t.side() == TradeSide.BUY ? money.negate() : money;
            fromTrades = fromTrades.add(signed).subtract(t.commission());
        }
        return externalNet.add(fromTrades).add(receivedIncome);
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
    public BigDecimal reportedBalance(boolean cashTracked, BigDecimal externalNet,
                                      List<PricedTrade> trades, BigDecimal receivedIncome) {
        return cashTracked ? balance(externalNet, trades, receivedIncome) : Money.ZERO;
    }
}
