package org.example.domain.portfolio.tax;

import org.springframework.stereotype.Component;

/**
 * ИИС тип Б: прибыль от продаж и купоны освобождены от НДФЛ; дивиденды облагаются 13%.
 */
@Component
public class IisTypeBTaxStrategy extends AbstractTaxStrategy {

    @Override
    protected boolean gainTaxable() {
        return false;
    }

    @Override
    protected boolean couponTaxable() {
        return false;
    }

    @Override
    protected double ldvExemptGain(TaxContext ctx) {
        return 0.0;
    }

    @Override
    protected String notes() {
        return "ИИС тип Б: прибыль и купоны освобождены; дивиденды облагаются 13%.";
    }
}
