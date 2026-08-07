package org.example.domain.portfolio;

import org.example.domain.model.AmortizationPayment;
import org.example.domain.model.CouponPayment;
import org.example.domain.model.DividendPayment;
import org.example.domain.money.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Чистый прогнозатор доходов по позиции: купоны и амортизации по графику биржи,
 * дивиденды — только по объявленной выплате.
 * <p>
 * Купон приходит сюда уже достроенным ({@code CouponScheduleEstimator}): у флоатера будущие
 * периоды не зафиксированы, и такие события помечаются {@link IncomeEvent#estimate()}.
 */
@Component
public class IncomeForecaster {

    public List<IncomeEvent> bondIncome(String secId, BigDecimal quantity,
                                        List<CouponPayment> coupons,
                                        List<AmortizationPayment> amortizations,
                                        LocalDate from) {
        List<IncomeEvent> events = new ArrayList<>();
        for (CouponPayment c : coupons) {
            if (!c.date().isBefore(from)) {
                // Незафиксированный купон флоатера доходит сюда оценкой и таким же должен показаться
                events.add(new IncomeEvent(c.date(), secId, IncomeKind.COUPON,
                        Money.of(c.perBond()).multiply(quantity), c.estimated()));
            }
        }
        for (AmortizationPayment a : amortizations) {
            if (!a.date().isBefore(from)) {
                events.add(new IncomeEvent(a.date(), secId, IncomeKind.AMORTIZATION,
                        Money.of(a.perBond()).multiply(quantity), false));
            }
        }
        return events;
    }

    public List<IncomeEvent> dividendIncome(String secId, BigDecimal quantity,
                                            List<DividendPayment> dividends,
                                            LocalDate from) {
        if (!Money.isPositive(quantity)) {
            return List.of();
        }
        // Только реально объявленные MOEX выплаты с будущей датой закрытия реестра.
        // Прикидку «по прошлогоднему дивиденду» намеренно не строим: для разовых выплат
        // (как VTBR 25.58 за 2025) она вводит в заблуждение. Нет объявления — нет строки;
        // MOEX публикует дивиденд в dividends.json только после фиксации даты отсечки.
        return dividends.stream()
                .filter(d -> !d.recordDate().isBefore(from))
                .sorted(Comparator.comparing(DividendPayment::recordDate))
                .map(d -> new IncomeEvent(d.recordDate(), secId, IncomeKind.DIVIDEND,
                        Money.of(d.perShare()).multiply(quantity), false))
                .toList();
    }
}
