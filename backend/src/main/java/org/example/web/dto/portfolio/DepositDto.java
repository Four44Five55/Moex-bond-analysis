package org.example.web.dto.portfolio;

import org.example.domain.model.PayoutPeriod;

import java.time.LocalDate;

/**
 * Вклад с посчитанными величинами — фронт ничего не досчитывает.
 *
 * @param currentValue   стоимость сегодня: тело плюс начисленные, но ещё не выплаченные проценты
 * @param accruedNotPaid проценты, уже заработанные, но пока не полученные на руки
 * @param totalInterest  проценты за весь срок вклада
 * @param nextPayoutOn   дата ближайшего поступления процентов (null — если срок уже вышел)
 */
public record DepositDto(
        Long id,
        String name,
        double principal,
        double ratePct,
        LocalDate openedOn,
        LocalDate maturesOn,
        boolean capitalized,
        PayoutPeriod payoutPeriod,
        String note,
        double currentValue,
        double accruedNotPaid,
        double totalInterest,
        LocalDate nextPayoutOn,
        boolean matured
) {
}
