package org.example.web.dto.portfolio;

import org.example.domain.portfolio.deposit.DepositTaxBreakdown;

import org.example.domain.money.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * Сводка по всем счетам клиента разом — «сколько у меня всего и как это распределено».
 *
 * @param totals         суммы по всем счетам; {@code positionsCount} — число позиций во всех счетах
 * @param accounts       строки счетов в порядке создания
 * @param byAccount      распределение капитала по счетам
 * @param byType         распределение по типу бумаг (облигации/акции) по всем счетам сразу
 * @param upcomingIncome ближайшие выплаты по всем счетам и вкладам, по возрастанию даты
 * @param xirrPct        годовая доходность по всем денежным потокам сразу, % (null — если не определена)
 * @param deposits       банковские вклады с посчитанной стоимостью
 * @param depositsValue  стоимость вкладов сегодня: тело плюс начисленные проценты
 * @param grandTotal     весь капитал: бумаги + свободные деньги + вклады
 * @param depositTax     налог с процентов по вкладам — считается на уровне клиента, а не счёта
 */
public record ConsolidatedOverviewDto(
        PortfolioSummaryDto totals,
        List<AccountLineDto> accounts,
        List<AllocationSliceDto> byAccount,
        List<AllocationSliceDto> byType,
        List<IncomeEventDto> upcomingIncome,
        Double xirrPct,
        List<DepositDto> deposits,
        BigDecimal depositsValue,
        BigDecimal grandTotal,
        DepositTaxBreakdown depositTax
) {

    public static ConsolidatedOverviewDto empty() {
        return new ConsolidatedOverviewDto(
                new PortfolioSummaryDto(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, null, null,
                        Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, 0),
                List.of(), List.of(), List.of(), List.of(), null,
                List.of(), Money.ZERO, Money.ZERO, DepositTaxBreakdown.zero());
    }
}
