package org.example.web.dto.portfolio;

import org.example.domain.model.TradeSide;
import org.example.domain.model.Transaction;

import java.time.LocalDate;

public record TransactionDto(
        Long id,
        String secId,
        TradeSide side,
        LocalDate tradeDate,
        double quantity,
        double price,
        double aci,
        double commission,
        String note
) {
    public static TransactionDto from(Transaction t) {
        return new TransactionDto(t.getId(), t.getSecId(), t.getSide(), t.getTradeDate(),
                t.getQuantity(), t.getPrice(), t.getAci(), t.getCommission(), t.getNote());
    }
}
