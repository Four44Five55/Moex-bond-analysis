package org.example.web.dto.portfolio;

import org.example.domain.model.Account;
import org.example.domain.model.AccountType;
import org.example.domain.model.IisType;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountDto(
        Long id,
        String name,
        AccountType type,
        IisType iisType,
        BigDecimal defaultCommissionPct,
        BigDecimal baseTaxRate,
        Instant createdAt
) {
    public static AccountDto from(Account a) {
        return new AccountDto(a.getId(), a.getName(), a.getType(), a.getIisType(),
                a.getDefaultCommissionPct(), a.getBaseTaxRate(), a.getCreatedAt());
    }
}
