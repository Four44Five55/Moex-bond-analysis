package org.example.domain.portfolio.history;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Входные данные для построения ряда стоимости портфеля (Builder): сделки счёта,
 * ценовые ряды по бумагам и границы периода. Собирается пошагово в application-слое,
 * потому что цены дочитываются по одной бумаге за раз.
 */
public final class PortfolioValueRequest {

    private final List<DatedTrade> trades;
    private final Map<String, NavigableMap<LocalDate, Double>> closesBySecId;
    private final LocalDate from;
    private final LocalDate to;

    private PortfolioValueRequest(Builder builder) {
        this.trades = List.copyOf(builder.trades);
        this.closesBySecId = Map.copyOf(builder.closesBySecId);
        this.from = builder.from;
        this.to = builder.to;
    }

    public List<DatedTrade> trades() {
        return trades;
    }

    public LocalDate from() {
        return from;
    }

    public LocalDate to() {
        return to;
    }

    /** Цена закрытия бумаги на дату или на ближайший предшествующий торговый день; 0, если истории нет. */
    public double closeAsOf(String secId, LocalDate date) {
        NavigableMap<LocalDate, Double> closes = closesBySecId.get(secId);
        if (closes == null) {
            return 0.0;
        }
        Map.Entry<LocalDate, Double> entry = closes.floorEntry(date);
        return entry == null ? 0.0 : entry.getValue();
    }

    public static Builder builder(LocalDate from, LocalDate to) {
        return new Builder(from, to);
    }

    public static final class Builder {
        private final List<DatedTrade> trades = new ArrayList<>();
        private final Map<String, NavigableMap<LocalDate, Double>> closesBySecId = new HashMap<>();
        private final LocalDate from;
        private final LocalDate to;

        private Builder(LocalDate from, LocalDate to) {
            if (from == null || to == null || from.isAfter(to)) {
                throw new IllegalArgumentException("Некорректный период ряда стоимости");
            }
            this.from = from;
            this.to = to;
        }

        public Builder trades(List<DatedTrade> value) {
            trades.addAll(value);
            return this;
        }

        /** Ценовой ряд одной бумаги: дата → цена закрытия в валюте. */
        public Builder closes(String secId, Map<LocalDate, Double> closes) {
            closesBySecId.computeIfAbsent(secId, k -> new TreeMap<>()).putAll(closes);
            return this;
        }

        public PortfolioValueRequest build() {
            return new PortfolioValueRequest(this);
        }
    }
}
