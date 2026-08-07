package org.example.web.dto.portfolio;

import org.example.domain.model.CashDirection;
import org.example.domain.model.CashMovement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashMovementDto(
        Long id,
        CashDirection direction,
        LocalDate movedOn,
        BigDecimal amount,
        String note
) {

    public static CashMovementDto from(CashMovement m) {
        return new CashMovementDto(m.getId(), m.getDirection(), m.getMovedOn(), m.getAmount(), m.getNote());
    }
}
