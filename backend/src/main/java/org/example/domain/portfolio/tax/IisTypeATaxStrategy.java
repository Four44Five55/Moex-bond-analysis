package org.example.domain.portfolio.tax;

import org.example.domain.money.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * ИИС тип А: доход облагается как на брокерском счёте, но ЛДВ не применяется
 * (льгота типа А — вычет на взносы, здесь не моделируется).
 */
@Component
public class IisTypeATaxStrategy extends AbstractTaxStrategy {

    @Override
    protected boolean gainTaxable() {
        return true;
    }

    @Override
    protected boolean couponTaxable() {
        return true;
    }

    @Override
    protected BigDecimal ldvExemptGain(TaxContext ctx) {
        return Money.ZERO;
    }

    @Override
    protected String notes() {
        return "ИИС тип А: НДФЛ на прибыль и купоны без ЛДВ; вычет на взносы вне расчёта.";
    }
}
