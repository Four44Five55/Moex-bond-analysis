package org.example.web.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param month месяц в формате {@code YYYY-MM} — строкой, а не {@code YearMonth}: так ответ
 *              не зависит от настроек сериализации дат, а фронт разбирает его без библиотеки
 */
public record CalendarMonthDto(String month, BigDecimal gross, BigDecimal net, int count,
                               List<CalendarKindDto> byKind) {
}
