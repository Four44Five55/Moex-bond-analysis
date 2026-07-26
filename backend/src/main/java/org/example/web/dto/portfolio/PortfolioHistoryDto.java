package org.example.web.dto.portfolio;

import java.util.List;

/**
 * История портфеля: дневной ряд стоимости и годовая доходность XIRR в процентах.
 *
 * @param xirrPct {@code null}, если доходность не определена — например, счёт только
 *                пополнялся и ещё ничего не возвращал
 */
public record PortfolioHistoryDto(List<ValuePointDto> points, Double xirrPct) {

    public static PortfolioHistoryDto empty() {
        return new PortfolioHistoryDto(List.of(), null);
    }
}
