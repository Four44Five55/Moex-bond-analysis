package org.example.domain.port;

import org.example.domain.model.AccruedInterest;

import java.time.LocalDate;

/** Порт истории торгов по облигации: то, что нельзя вывести из справочника и графика купонов. */
public interface BondHistoryProvider {

    /**
     * НКД на одну облигацию на дату сделки. Если в этот день торгов не было, берётся ближайший
     * предшествующий торговый день.
     */
    AccruedInterest accruedInterestOn(String secId, LocalDate date);
}
