package org.example.web.dto.portfolio;

import java.math.BigDecimal;

/**
 * Три разных величины, которые легко перепутать и нельзя складывать:
 * <ul>
 *   <li>{@code externalIn} — сколько денег внесено <b>извне</b> (пополнения минус выводы,
 *       плюс тело вкладов). Только это «свои» деньги;</li>
 *   <li>{@code invested} — себестоимость открытых позиций. Сюда входят и реинвестированные
 *       выплаты, поэтому она растёт без единого нового рубля извне;</li>
 *   <li>{@code receivedIncome} — полученные купоны и дивиденды за всю историю. Реинвестированные
 *       из них уже сидят в {@code invested} и {@code marketValue}, поэтому прибавлять их
 *       к капиталу — считать один и тот же рубль дважды.</li>
 * </ul>
 * Итог сходится по одному тождеству: {@code totalProfit = totalValue − externalIn}.
 *
 * @param cashBalance  свободные деньги: пополнения − выводы − покупки − комиссии + продажи + выплаты.
 *                     Ноль, если движения денег по счёту не заведены — тогда остаток неизвестен
 * @param totalValue   весь капитал счёта: бумаги по рынку плюс свободные деньги
 * @param externalIn   внесено извне; {@code null}, если учёт движений денег не ведётся
 * @param totalProfit  заработано за всё время: капитал минус внесённое. Включает и рост цены,
 *                     и полученные выплаты, и убытки от продаж — всё сразу и без двойного счёта.
 *                     {@code null}, если неизвестно, сколько внесено
 */
public record PortfolioSummaryDto(
        BigDecimal invested,
        BigDecimal marketValue,
        BigDecimal cashBalance,
        BigDecimal totalValue,
        BigDecimal externalIn,
        BigDecimal totalProfit,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        BigDecimal totalCommission,
        BigDecimal receivedIncome,
        BigDecimal projectedAnnualIncome,
        BigDecimal estimatedTax,
        int positionsCount
) {
}
