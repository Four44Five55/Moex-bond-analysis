package org.example.domain.portfolio.calendar;

import java.util.List;

/**
 * Календарь выплат за горизонт: помесячные суммы и итог по нему.
 *
 * @param averageMonthlyNet среднее «на руки» за месяц — делится на длину горизонта, а не на число
 *                          месяцев с выплатами: пустые месяцы тоже часть срока, и исключать их
 *                          значило бы завышать регулярный доход
 */
public record PaymentCalendar(List<PaymentCalendarMonth> months, double totalGross, double totalNet,
                              double averageMonthlyNet) {

    public static PaymentCalendar empty() {
        return new PaymentCalendar(List.of(), 0, 0, 0);
    }
}
