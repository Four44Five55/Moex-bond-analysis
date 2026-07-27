package org.example.domain.model;

import java.time.LocalDate;

/**
 * НКД на одну облигацию по данным биржи.
 * <p>
 * Расчётом это не получить: у флоатеров ставка купона на будущие периоды не зафиксирована,
 * зато НКД биржа считает и публикует по каждому торговому дню.
 *
 * @param tradeDate день, за который взято значение — не обязательно запрошенный: в выходные
 *                  и праздники торгов нет, и берётся ближайший предшествующий
 * @param known     {@code false}, если данных за период не нашлось; {@code perBond} тогда 0
 *                  (отдельный флаг вместо {@code null} — значение кешируется, а кеш
 *                  не хранит null)
 */
public record AccruedInterest(LocalDate tradeDate, double perBond, boolean known) {

    public static AccruedInterest unknown() {
        return new AccruedInterest(null, 0.0, false);
    }
}
