package org.example.web.dto.portfolio;

import java.util.List;

/**
 * Календарь выплат по всему капиталу клиента: помесячные суммы плюс сами выплаты за горизонт.
 * <p>
 * Плоский список отдаётся вместе с месяцами намеренно: фронт группирует его по месяцу только
 * для показа деталей выбранного столбца, а все суммы берёт из {@code months} — считает бэкенд.
 *
 * @param months          месяцы горизонта по возрастанию, включая месяцы без выплат
 * @param payments        выплаты за горизонт по возрастанию даты
 * @param totalGross      всё, что поступит за горизонт — это прогноз, а не начисленное
 * @param incomeGross     из него доход: без возврата номинала по амортизациям
 * @param principalReturn возврат номинала — вложенное возвращается, доходом не является
 */
public record PaymentCalendarDto(List<CalendarMonthDto> months,
                                 double totalGross,
                                 double incomeGross,
                                 double principalReturn,
                                 double totalNet,
                                 double averageMonthlyNet,
                                 List<IncomeEventDto> payments,
                                 int horizonMonths) {

    public static PaymentCalendarDto empty(int horizonMonths) {
        return new PaymentCalendarDto(List.of(), 0, 0, 0, 0, 0, List.of(), horizonMonths);
    }
}
