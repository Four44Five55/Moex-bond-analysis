package org.example.domain.portfolio.allocation;

import org.example.domain.model.Bond;
import org.example.domain.model.Instrument;
import org.example.domain.model.Share;
import org.springframework.stereotype.Component;

/** Аллокация по сектору: у акций — сектор MOEX, облигации сведены в одну категорию. */
@Component
public class BySectorDimension implements AllocationDimension {

    @Override
    public String key() {
        return "sector";
    }

    @Override
    public String categoryOf(Instrument instrument) {
        if (instrument instanceof Share share) {
            String sector = share.getSector();
            return sector != null && !sector.isBlank() ? sector : "Не указан";
        }
        if (instrument instanceof Bond) {
            return "Облигации";
        }
        return "Прочее";
    }
}
