package org.example.domain.portfolio.history;

import java.time.LocalDate;

/**
 * Денежный поток портфеля на дату. Знак — с точки зрения инвестора:
 * отрицательный — деньги вложены (покупка, комиссия), положительный — получены
 * (продажа, купон, дивиденд, а также текущая стоимость позиций на конец периода).
 */
public record CashFlow(LocalDate date, double amount) {
}
