package org.example.domain.portfolio.tax;

import org.example.domain.money.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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
    protected BigDecimal ldvExemptGain(TaxContext ctx) {
        return Money.ZERO;
    }

    @Override
    protected String notes() {
        return "ИИС тип Б: прибыль и купоны освобождены; дивиденды облагаются 13%.";
    }
}
