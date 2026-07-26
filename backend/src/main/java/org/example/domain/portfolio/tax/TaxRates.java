package org.example.domain.portfolio.tax;

/**
 * Ставки НДФЛ — единственный источник истины. Нужны и стратегиям налога, и оценке выплат
 * «на руки» в доходном календаре: раньше календарь держал собственную копию 13%,
 * и ставки могли разъехаться при первом же изменении.
 */
public final class TaxRates {

    /** Дивиденды резидента РФ облагаются фиксированно и не зависят от типа счёта, включая ИИС. */
    public static final double DIVIDEND_PCT = 13.0;

    /** База сверх этого порога облагается по повышенной ставке. */
    public static final double PROGRESSIVE_THRESHOLD = 5_000_000.0;

    public static final double HIGH_PCT = 15.0;

    private TaxRates() {
    }
}
