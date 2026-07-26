package org.example.domain.portfolio.allocation;

import org.example.domain.model.Instrument;
import org.springframework.stereotype.Component;

/** Аллокация по валюте инструмента (пустая валюта трактуется как RUB). */
@Component
public class ByCurrencyDimension implements AllocationDimension {

    @Override
    public String key() {
        return "currency";
    }

    @Override
    public String categoryOf(Instrument instrument) {
        if (instrument == null) {
            return "Прочее";
        }
        String currency = instrument.getCurrency();
        return currency != null && !currency.isBlank() ? currency : "RUB";
    }
}
