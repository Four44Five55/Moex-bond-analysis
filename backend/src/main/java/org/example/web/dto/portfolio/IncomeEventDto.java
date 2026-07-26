package org.example.web.dto.portfolio;

import org.example.domain.portfolio.IncomeKind;

import java.time.LocalDate;

/**
 * @param accountName счёт, по которому пройдёт выплата — в сводном календаре по всем счетам
 *                    без него не понять, куда придут деньги.
 */
public record IncomeEventDto(
        LocalDate date,
        String secId,
        IncomeKind kind,
        double gross,
        double net,
        boolean estimate,
        String accountName
) {
}
