package org.example.domain.portfolio.defaults;

import org.example.domain.model.BondDefaultFlags;

import java.time.LocalDate;

/**
 * Дефолтное положение одной бумаги для клиента: что говорит биржа и что отметил сам инвестор.
 *
 * @param moex          признаки MOEX
 * @param markedOn      дата из отметки клиента, с которой выплаты не поступают; {@code null} — не отмечено
 * @param note          комментарий к отметке
 * @param cutoff        с этой даты платежи графика не считаются ни полученными, ни ожидаемыми.
 *                      Если клиент даты не задал, а MOEX признаёт дефолт, — сегодня: будущих
 *                      выплат уже не ждём, а прошедшие оставляем как были, пока инвестор
 *                      не скажет, с какой именно выплаты деньги перестали приходить.
 *                      {@code null} — бумага платит
 */
public record DefaultState(BondDefaultFlags moex, LocalDate markedOn, String note, LocalDate cutoff) {

    public static DefaultState of(BondDefaultFlags moex, LocalDate markedOn, String note, LocalDate today) {
        LocalDate cutoff = markedOn != null ? markedOn : (moex.hasDefault() ? today : null);
        return new DefaultState(moex, markedOn, note, cutoff);
    }

    /** Платёж графика на эту дату действительно пришёл (или придёт). */
    public boolean paid(LocalDate date) {
        return cutoff == null || date.isBefore(cutoff);
    }

    public boolean defaulted() {
        return cutoff != null;
    }

    /** Дата прекращения выплат названа инвестором, а не подставлена по умолчанию. */
    public boolean confirmed() {
        return markedOn != null;
    }

    /** Есть о чём предупредить: дефолт по любому из источников, включая технический. */
    public boolean flagged() {
        return defaulted() || moex.any();
    }
}
