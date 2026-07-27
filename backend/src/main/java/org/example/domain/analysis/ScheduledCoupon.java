package org.example.domain.analysis;

import java.time.LocalDate;

/**
 * Строка графика купонов в том виде, в каком её отдаёт биржа.
 *
 * @param periodStart начало купонного периода; {@code null}, если биржа его не назвала
 * @param faceValue   номинал, на который начисляется этот купон — у амортизируемого выпуска
 *                    он падает от периода к периоду
 * @param value       сумма купона на облигацию; {@code null} — купон ещё не зафиксирован
 *                    (плавающая ставка), и его предстоит оценить
 */
public record ScheduledCoupon(LocalDate date, LocalDate periodStart, double faceValue, Double value) {
}
