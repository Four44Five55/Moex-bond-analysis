package org.example.domain.filter;

import org.example.domain.model.Share;
import org.springframework.data.jpa.domain.Specification;

/** Компонуемые JPA-спецификации для акций. */
public final class ShareSpecifications {

    private ShareSpecifications() {
    }

    public static Specification<Share> fromCriteria(FilterCriteria c) {
        Specification<Share> spec = Specification.where(null);
        if (c.getQuery() != null && !c.getQuery().isBlank()) {
            spec = spec.and(matchesQuery(c.getQuery().trim()));
        }
        if (c.getSector() != null && !c.getSector().isBlank()) {
            spec = spec.and(bySector(c.getSector().trim()));
        }
        return spec;
    }

    public static Specification<Share> matchesQuery(String q) {
        String like = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("shortName")), like),
                cb.like(cb.lower(root.get("fullName")), like),
                cb.like(cb.lower(root.get("secId")), like),
                cb.like(cb.lower(root.get("isin")), like)
        );
    }

    public static Specification<Share> bySector(String sector) {
        return (root, query, cb) -> cb.equal(root.get("sector"), sector);
    }
}
