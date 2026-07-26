package org.example.domain.portfolio.allocation;

import org.example.domain.model.Bond;
import org.example.domain.model.Instrument;
import org.example.domain.model.Share;
import org.springframework.stereotype.Component;

/** Аллокация по типу инструмента: облигации / акции. */
@Component
public class ByTypeDimension implements AllocationDimension {

    @Override
    public String key() {
        return "type";
    }

    @Override
    public String categoryOf(Instrument instrument) {
        if (instrument instanceof Bond) {
            return "Облигации";
        }
        if (instrument instanceof Share) {
            return "Акции";
        }
        return "Прочее";
    }
}
