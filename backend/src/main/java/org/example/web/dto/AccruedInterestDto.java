package org.example.web.dto;

import java.time.LocalDate;

/**
 * НКД облигации на дату сделки — для подстановки в форму ввода.
 *
 * @param bond     {@code false} для акции: НКД у неё нет, и поле в форме показывать незачем
 * @param aci      НКД на одну облигацию; {@code null} — определить не удалось, пользователь
 *                 вписывает вручную
 * @param quotedOn торговый день, за который взято значение: в выходные берётся ближайший
 *                 предшествующий, и это стоит показать, чтобы цифра не выглядела взятой с потолка
 */
public record AccruedInterestDto(String secId, LocalDate date, boolean bond,
                                 Double aci, LocalDate quotedOn) {

    public static AccruedInterestDto notABond(String secId, LocalDate date) {
        return new AccruedInterestDto(secId, date, false, null, null);
    }

    public static AccruedInterestDto unknown(String secId, LocalDate date) {
        return new AccruedInterestDto(secId, date, true, null, null);
    }
}
