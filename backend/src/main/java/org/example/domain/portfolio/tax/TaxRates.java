package org.example.domain.portfolio.tax;

import org.example.domain.money.Money;

import java.math.BigDecimal;

/**
 * Ставки НДФЛ — единственный источник истины. Нужны и стратегиям налога, и оценке выплат
 * «на руки» в доходном календаре: раньше календарь держал собственную копию 13%,
 * и ставки могли разъехаться при первом же изменении.
 */
public final class TaxRates {

    /** Дивиденды резидента РФ облагаются фиксированно и не зависят от типа счёта, включая ИИС. */
    public static final BigDecimal DIVIDEND_PCT = Money.of("13.00");

    /** База сверх этого порога облагается по повышенной ставке. */
    public static final BigDecimal PROGRESSIVE_THRESHOLD = Money.of("5000000.00");

    public static final BigDecimal HIGH_PCT = Money.of("15.00");

    public static final BigDecimal BASE_PCT = Money.of("13.00");

    /**
     * База необлагаемого вычета по процентам вкладов: 1 млн ₽ × максимальная ключевая ставка
     * за год. Сама ставка задаётся конфигурацией — источника в MOEX ISS для неё нет.
     */
    public static final BigDecimal DEPOSIT_DEDUCTION_BASE = Money.of("1000000.00");

    private TaxRates() {
    }
}
