package org.example.domain.analysis;

import org.example.domain.model.Bond;
import org.springframework.stereotype.Component;

/**
 * Прокси-оценка надёжности эмитента облигации.
 *
 * <p>У MOEX ISS нет прямого кредитного рейтинга, поэтому надёжность выводится из
 * уровня листинга и признака государственной бумаги (ОФЗ). Инкапсулировано отдельной
 * стратегией, чтобы легко заменить на реальный источник (АКРА / Эксперт РА / НКР).
 */
@Component
public class ReliabilityScorer {

    /** Заполняет reliabilityScore, issuerRating и investmentGrade у облигации. */
    public void score(Bond bond, int listLevel) {
        int base = switch (listLevel) {
            case 1 -> 8;
            case 2 -> 5;
            case 3 -> 2;
            default -> 3;
        };

        boolean government = isGovernmentBond(bond);
        int score = Math.max(1, Math.min(government ? base + 2 : base, 10));

        bond.setListLevel(listLevel);
        bond.setReliabilityScore(score);
        bond.setInvestmentGrade(score >= 7);
        bond.setIssuerRating(government ? "ОФЗ" : (listLevel > 0 ? "Листинг " + listLevel : "—"));
    }

    private boolean isGovernmentBond(Bond bond) {
        String secId = bond.getSecId();
        String fullName = bond.getFullName();
        String shortName = bond.getShortName();
        return (secId != null && secId.startsWith("SU"))
                || containsOfz(fullName)
                || containsOfz(shortName);
    }

    private boolean containsOfz(String value) {
        return value != null && value.toUpperCase().contains("ОФЗ");
    }
}
