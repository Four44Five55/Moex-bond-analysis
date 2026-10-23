package org.example.web.dto.portfolio;

import org.example.domain.model.DefaultRecovery;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DefaultRecoveryDto(
        Long id,
        String secId,
        LocalDate receivedOn,
        BigDecimal amount,
        String note
) {

    public static DefaultRecoveryDto from(DefaultRecovery r) {
        return new DefaultRecoveryDto(r.getId(), r.getSecId(), r.getReceivedOn(), r.getAmount(), r.getNote());
    }
}
