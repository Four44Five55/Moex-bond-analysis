package org.example.domain.filter;

import org.example.domain.model.Instrument;
import org.springframework.data.jpa.domain.Specification;

/**
 * Спецификации по полиморфной базе {@link Instrument} — общие поля
 * (для смешанного списка облигаций и акций).
 */
public final class InstrumentSpecifications {

    private InstrumentSpecifications() {
    }

    public static Specification<Instrument> fromCriteria(FilterCriteria c) {
        Specification<Instrument> spec = Specification.where(null);
        if (c.getQuery() != null && !c.getQuery().isBlank()) {
            spec = spec.and(matchesQuery(c.getQuery().trim()));
        }
        return spec;
    }

    public static Specification<Instrument> matchesQuery(String q) {
        String like = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("shortName")), like),
                cb.like(cb.lower(root.get("fullName")), like),
                cb.like(cb.lower(root.get("secId")), like),
                cb.like(cb.lower(root.get("isin")), like)
        );
    }
}
