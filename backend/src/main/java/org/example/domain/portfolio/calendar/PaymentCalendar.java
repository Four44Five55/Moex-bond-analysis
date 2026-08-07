package org.example.domain.portfolio.calendar;

import org.example.domain.money.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * Календарь выплат за горизонт: помесячные суммы и итог по нему.
 * <p>
 * Все суммы — <b>будущие поступления</b>, а не начисленное: в календарь входят только выплаты
 * с сегодняшнего дня и дальше.
 *
 * @param averageMonthlyNet среднее «на руки» за месяц — делится на длину горизонта, а не на число
 *                          месяцев с выплатами: пустые месяцы тоже часть срока, и исключать их
 *                          значило бы завышать регулярный доход
 * @param totalByKind       итог по видам выплат за весь горизонт
 * @param principalReturn   из общей суммы — возврат номинала (амортизации). Это не доход,
 *                          а возврат вложенного, и в одну строку с купонами его сводить нельзя:
 *                          у портфеля с амортизируемыми выпусками он легко больше самих купонов
 */
public record PaymentCalendar(List<PaymentCalendarMonth> months, BigDecimal totalGross,
                              BigDecimal totalNet, BigDecimal averageMonthlyNet,
                              List<PaymentKindSlice> totalByKind, BigDecimal principalReturn) {

    public static PaymentCalendar empty() {
        return new PaymentCalendar(List.of(), Money.ZERO, Money.ZERO, Money.ZERO,
                List.of(), Money.ZERO);
    }

    /** Доход за горизонт: всё, что придёт, минус возврат номинала. */
    public BigDecimal incomeGross() {
        return totalGross.subtract(principalReturn);
    }
}
