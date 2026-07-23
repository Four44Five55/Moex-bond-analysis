package org.example.domain.portfolio;

import org.example.domain.model.AmortizationPayment;
import org.example.domain.model.CouponPayment;
import org.example.domain.model.DividendPayment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Чистый прогнозатор доходов по позиции: купоны/амортизации точно по графику;
 * дивиденды — по анонсированной будущей выплате, иначе оценка по последней (год вперёд).
 */
@Component
public class IncomeForecaster {

    public List<IncomeEvent> bondIncome(String secId, double quantity,
                                        List<CouponPayment> coupons,
                                        List<AmortizationPayment> amortizations,
                                        LocalDate from) {
        List<IncomeEvent> events = new ArrayList<>();
        for (CouponPayment c : coupons) {
            if (!c.date().isBefore(from)) {
                events.add(new IncomeEvent(c.date(), secId, IncomeKind.COUPON, c.perBond() * quantity, false));
            }
        }
        for (AmortizationPayment a : amortizations) {
            if (!a.date().isBefore(from)) {
                events.add(new IncomeEvent(a.date(), secId, IncomeKind.AMORTIZATION, a.perBond() * quantity, false));
            }
        }
        return events;
    }

    public List<IncomeEvent> dividendIncome(String secId, double quantity,
                                            List<DividendPayment> dividends,
                                            LocalDate from) {
        if (dividends.isEmpty() || quantity <= 0) {
            return List.of();
        }
        List<DividendPayment> sorted = dividends.stream()
                .sorted(Comparator.comparing(DividendPayment::recordDate))
                .toList();

        List<DividendPayment> future = sorted.stream()
                .filter(d -> !d.recordDate().isBefore(from))
                .toList();

        if (!future.isEmpty()) {
            return future.stream()
                    .map(d -> new IncomeEvent(d.recordDate(), secId, IncomeKind.DIVIDEND,
                            d.perShare() * quantity, false))
                    .toList();
        }

        // Оценка: последняя известная выплата, спроецированная на год вперёд
        DividendPayment last = sorted.get(sorted.size() - 1);
        return List.of(new IncomeEvent(last.recordDate().plusYears(1), secId, IncomeKind.DIVIDEND,
                last.perShare() * quantity, true));
    }
}
