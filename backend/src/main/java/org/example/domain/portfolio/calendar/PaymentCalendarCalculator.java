package org.example.domain.portfolio.calendar;

import org.example.domain.portfolio.IncomeKind;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Раскладка выплат по месяцам горизонта. Расчёт чистый: без БД и сети.
 * <p>
 * Горизонт задаётся первым месяцем и их количеством, а не парой дат: календарь показывает
 * месяцы целиком, и «12 месяцев с сегодня» на графике выглядело бы как 13 столбцов,
 * первый и последний из которых обрезаны.
 */
@Component
public class PaymentCalendarCalculator {

    /**
     * @param from   первый месяц горизонта включительно
     * @param months длина горизонта в месяцах; {@code <= 0} даёт пустой календарь
     * @return месяцы по возрастанию, включая месяцы без выплат
     */
    public PaymentCalendar build(List<PaymentCalendarEntry> entries, YearMonth from, int months) {
        if (months <= 0) {
            return PaymentCalendar.empty();
        }

        Map<YearMonth, Bucket> buckets = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            buckets.put(from.plusMonths(i), new Bucket());
        }

        Bucket horizon = new Bucket();
        for (PaymentCalendarEntry e : entries) {
            Bucket bucket = buckets.get(YearMonth.from(e.date()));
            if (bucket == null) {
                continue; // выплата вне горизонта — календарь её не показывает и в итог не берёт
            }
            bucket.add(e);
            horizon.add(e);
        }

        List<PaymentCalendarMonth> result = new ArrayList<>(months);
        buckets.forEach((month, bucket) -> result.add(bucket.toMonth(month)));
        return new PaymentCalendar(List.copyOf(result), horizon.gross, horizon.net,
                horizon.net / months, horizon.slices(), horizon.principalReturn());
    }

    private static final class Bucket {
        /** EnumMap — порядок видов выплат один и тот же во всех месяцах. */
        private final Map<IncomeKind, double[]> byKind = new EnumMap<>(IncomeKind.class);
        private double gross;
        private double net;
        private int count;

        private void add(PaymentCalendarEntry e) {
            double[] sums = byKind.computeIfAbsent(e.kind(), k -> new double[2]);
            sums[0] += e.gross();
            sums[1] += e.net();
            gross += e.gross();
            net += e.net();
            count++;
        }

        private PaymentCalendarMonth toMonth(YearMonth month) {
            return new PaymentCalendarMonth(month, gross, net, count, slices());
        }

        private List<PaymentKindSlice> slices() {
            return byKind.entrySet().stream()
                    .map(en -> new PaymentKindSlice(en.getKey(), en.getValue()[0], en.getValue()[1]))
                    .toList();
        }

        /** Амортизация — возврат тела долга, а не доход: в календаре она есть, в доходе её быть не должно. */
        private double principalReturn() {
            double[] sums = byKind.get(IncomeKind.AMORTIZATION);
            return sums == null ? 0.0 : sums[0];
        }
    }
}
