package org.example.domain.portfolio.defaults;

import java.time.LocalDate;
import java.util.Map;

/**
 * Дефолтное положение бумаг клиента: какие платежи графика MOEX считать настоящими.
 * Бумаги, которых здесь нет, платят по графику.
 */
public final class DefaultPolicy {

    private static final DefaultPolicy NONE = new DefaultPolicy(Map.of());

    private final Map<String, DefaultState> bySecId;

    public DefaultPolicy(Map<String, DefaultState> bySecId) {
        this.bySecId = Map.copyOf(bySecId);
    }

    public static DefaultPolicy none() {
        return NONE;
    }

    /** {@code null}, если по бумаге ни дефолта, ни отметки. */
    public DefaultState state(String secId) {
        return secId == null ? null : bySecId.get(secId);
    }

    public boolean paid(String secId, LocalDate date) {
        DefaultState state = state(secId);
        return state == null || state.paid(date);
    }

    public boolean defaulted(String secId) {
        DefaultState state = state(secId);
        return state != null && state.defaulted();
    }

    public Map<String, DefaultState> all() {
        return bySecId;
    }
}
