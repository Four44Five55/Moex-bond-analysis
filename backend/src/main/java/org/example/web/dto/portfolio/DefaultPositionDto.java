package org.example.web.dto.portfolio;

import java.time.LocalDate;

/**
 * Бумага в дефолте на одном счёте и потери по ней.
 *
 * @param paymentsStoppedOn с этой даты платежи графика не считаются полученными
 * @param confirmed         дату назвал инвестор; {@code false} — подставлено «сегодня» по признаку
 *                          MOEX, и уже прошедшие неоплаченные платежи всё ещё считаются полученными
 */
public record DefaultPositionDto(
        Long accountId,
        String accountName,
        String secId,
        String name,
        boolean moexDefault,
        boolean moexTechnicalDefault,
        LocalDate paymentsStoppedOn,
        boolean confirmed,
        String note,
        DefaultLossDto loss
) {
}
