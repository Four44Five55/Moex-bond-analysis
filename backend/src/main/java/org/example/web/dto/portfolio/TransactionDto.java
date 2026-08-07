package org.example.web.dto.portfolio;

import org.example.domain.model.TradeSide;
import org.example.domain.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param secName краткое название бумаги; {@code null}, если инструмента уже нет в справочнике —
 *                сделка от этого не перестаёт существовать
 */
public record TransactionDto(
        Long id,
        String secId,
        String secName,
        TradeSide side,
        LocalDate tradeDate,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal aci,
        BigDecimal commission,
        String note
) {
    public static TransactionDto from(Transaction t, String secName) {
        return new TransactionDto(t.getId(), t.getSecId(), secName, t.getSide(), t.getTradeDate(),
                t.getQuantity(), t.getPrice(), t.getAci(), t.getCommission(), t.getNote());
    }
}
