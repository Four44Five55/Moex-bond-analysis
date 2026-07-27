package org.example.web.dto.portfolio;

import org.example.domain.portfolio.IncomeKind;

import java.time.LocalDate;

/**
 * @param secName     краткое название бумаги: по коду вида RU000A10B2N2 не понять, чья это выплата.
 *                    {@code null} у процентов по вкладу — бумаги там нет
 * @param accountName счёт, по которому пройдёт выплата — в сводном календаре по всем счетам
 *                    без него не понять, куда придут деньги.
 */
public record IncomeEventDto(
        LocalDate date,
        String secId,
        String secName,
        IncomeKind kind,
        double gross,
        double net,
        boolean estimate,
        String accountName
) {
}
