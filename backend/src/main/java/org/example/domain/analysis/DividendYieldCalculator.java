package org.example.domain.analysis;

import org.example.domain.model.DividendPayment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Чистый расчёт дивидендной доходности за скользящие 12 месяцев:
 * сумма выплат на акцию за период / текущая цена. Без БД/сети — тестируется изолированно.
 */
@Component
public class DividendYieldCalculator {

    public DividendSummary trailing(List<DividendPayment> dividends, double price, LocalDate today) {
        if (dividends == null || dividends.isEmpty()) {
            return DividendSummary.empty();
        }
        LocalDate from = today.minusYears(1);
        double sum = 0.0;
        int count = 0;
        for (DividendPayment d : dividends) {
            LocalDate date = d.recordDate();
            if (date != null && !date.isBefore(from) && !date.isAfter(today)) {
                sum += d.perShare();
                count++;
            }
        }
        if (count == 0) {
            return DividendSummary.empty();
        }
        Double yieldPct = price > 0 ? sum / price * 100.0 : null;
        return new DividendSummary(sum, yieldPct, count);
    }
}
