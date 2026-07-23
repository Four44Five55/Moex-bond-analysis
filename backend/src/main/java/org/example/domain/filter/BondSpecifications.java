package org.example.domain.filter;

import org.example.domain.model.Bond;
import org.springframework.data.jpa.domain.Specification;

/**
 * Компонуемые JPA-спецификации для облигаций (паттерн Specification).
 * Каждый метод — независимый предикат; собираются через {@link Specification#and}.
 */
public final class BondSpecifications {

    private BondSpecifications() {
    }

    public static Specification<Bond> fromCriteria(FilterCriteria c) {
        Specification<Bond> spec = Specification.where(null);
        if (c.getQuery() != null && !c.getQuery().isBlank()) {
            spec = spec.and(matchesQuery(c.getQuery().trim()));
        }
        spec = spec.and(minYield(c.getMinYield()))
                .and(maxDuration(c.getMaxDuration()))
                .and(minReliability(c.getMinReliabilityScore()));
        if (c.isOnlyInvestmentGrade()) {
            spec = spec.and(investmentGradeOnly());
        }
        if (c.isExcludeSuspicious()) {
            spec = spec.and(notSuspicious());
        }
        return spec;
    }

    public static Specification<Bond> matchesQuery(String q) {
        String like = "%" + q.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("shortName")), like),
                cb.like(cb.lower(root.get("fullName")), like),
                cb.like(cb.lower(root.get("secId")), like),
                cb.like(cb.lower(root.get("isin")), like)
        );
    }

    public static Specification<Bond> minYield(double minYield) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("yieldToMaturity"), minYield);
    }

    public static Specification<Bond> maxDuration(double maxDuration) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("duration"), maxDuration);
    }

    public static Specification<Bond> minReliability(int minScore) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("reliabilityScore"), minScore);
    }

    public static Specification<Bond> investmentGradeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("investmentGrade"));
    }

    public static Specification<Bond> notSuspicious() {
        return (root, query, cb) -> cb.isFalse(root.get("suspiciousYield"));
    }
}
