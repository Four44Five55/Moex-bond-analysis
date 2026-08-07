package org.example.web.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Итог дозаполнения НКД в ранее заведённых сделках.
 * <p>
 * Разделено на примерку и применение: НКД меняет себестоимость позиций, налоговую базу и остаток
 * денег, поэтому сначала показываем, что именно изменится, и только потом пишем.
 *
 * @param dryRun     {@code true} — ничего не сохранялось, это примерка
 * @param candidates сделки с облигациями, у которых НКД не заполнен
 * @param filled     из них те, по которым биржа НКД назвала
 * @param costDelta  на сколько вырастет вложенная сумма по покупкам (по продажам — выручка)
 */
public record AciBackfillDto(boolean dryRun, int candidates, int filled, int unknown,
                             BigDecimal costDelta, List<Row> rows) {

    /**
     * @param quotedOn торговый день, за который взят НКД — в выходные это ближайший предшествующий
     * @param amount   {@code aci × количество}: столько добавится к стоимости этой сделки
     */
    public record Row(Long txId, String secId, String secName, LocalDate tradeDate, String side,
                      BigDecimal quantity, BigDecimal aci, LocalDate quotedOn, BigDecimal amount) {
    }
}
