package org.example.web.dto;

/**
 * Сводка по дивидендам акции за 12 месяцев: сумма на акцию, доходность (%), число выплат.
 * Для облигаций и при отсутствии данных поля null / 0.
 */
public record DividendInfoDto(
        Double trailingSum,
        Double dividendYield,
        int count
) {
}
