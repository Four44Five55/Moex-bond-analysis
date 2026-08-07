package org.example.domain.portfolio.calendar;

import org.example.domain.money.Money;
import org.example.domain.portfolio.IncomeKind;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
        BigDecimal averageNet = Money.divide(horizon.net, Money.of(months));
        return new PaymentCalendar(List.copyOf(result), horizon.gross, horizon.net,
                averageNet, horizon.slices(), horizon.principalReturn());
    }

    private static final class Bucket {
        /** EnumMap — порядок видов выплат один и тот же во всех месяцах. */
        private final Map<IncomeKind, Sums> byKind = new EnumMap<>(IncomeKind.class);
        private BigDecimal gross = Money.ZERO;
        private BigDecimal net = Money.ZERO;
        private int count;

        private void add(PaymentCalendarEntry e) {
            byKind.computeIfAbsent(e.kind(), k -> new Sums()).add(e);
            gross = gross.add(e.gross());
            net = net.add(e.net());
            count++;
        }

        private PaymentCalendarMonth toMonth(YearMonth month) {
            return new PaymentCalendarMonth(month, gross, net, count, slices());
        }

        private List<PaymentKindSlice> slices() {
            return byKind.entrySet().stream()
                    .map(en -> new PaymentKindSlice(en.getKey(), en.getValue().gross, en.getValue().net))
                    .toList();
        }

        /** Амортизация — возврат тела долга, а не доход: в календаре она есть, в доходе её быть не должно. */
        private BigDecimal principalReturn() {
            Sums sums = byKind.get(IncomeKind.AMORTIZATION);
            return sums == null ? Money.ZERO : sums.gross;
        }
    }

    /**
     * Пара накопителей на один вид выплат. Раньше это был {@code double[2]}, что на
     * {@link BigDecimal} не работает: массив хранил бы ссылки, и «прибавить» пришлось бы
     * записью обратно по индексу — с именованными полями это и короче, и понятнее.
     */
    private static final class Sums {
        private BigDecimal gross = Money.ZERO;
        private BigDecimal net = Money.ZERO;

        private void add(PaymentCalendarEntry e) {
            gross = gross.add(e.gross());
            net = net.add(e.net());
        }
    }
}
