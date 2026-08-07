package org.example.domain.portfolio.calendar;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Месяц календаря выплат. Месяцы без выплат тоже присутствуют с нулями: на графике провал
 * между поступлениями — такая же информация, как и сами поступления.
 *
 * @param byKind доли по видам выплат в порядке {@link org.example.domain.portfolio.IncomeKind},
 *               одинаковом для всех месяцев — иначе цвета в столбцах разъедутся
 */
public record PaymentCalendarMonth(YearMonth month, BigDecimal gross, BigDecimal net, int count,
                                   List<PaymentKindSlice> byKind) {
}
