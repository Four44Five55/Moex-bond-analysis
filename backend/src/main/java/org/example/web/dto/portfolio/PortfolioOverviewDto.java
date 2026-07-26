package org.example.web.dto.portfolio;

import org.example.domain.portfolio.tax.TaxBreakdown;

import java.util.List;

/**
 * Всё содержимое страницы счёта одним ответом. Существует ради того, чтобы страница
 * не заставляла сервер строить один и тот же снапшот портфеля по разу на каждую панель.
 *
 * @param income   прогноз предстоящих выплат
 * @param received фактически полученные выплаты, новыми вперёд
 */
public record PortfolioOverviewDto(PortfolioSummaryDto summary,
                                   List<PositionDto> positions,
                                   List<IncomeEventDto> income,
                                   List<IncomeEventDto> received,
                                   TaxBreakdown tax) {
}
