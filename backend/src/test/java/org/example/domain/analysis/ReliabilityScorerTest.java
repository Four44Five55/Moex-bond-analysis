package org.example.domain.analysis;

import org.example.domain.model.Bond;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReliabilityScorerTest {

    private final ReliabilityScorer scorer = new ReliabilityScorer();

    @Test
    void governmentBondGetsMaxScoreAndInvestmentGrade() {
        Bond bond = new Bond();
        bond.setSecId("SU26238RMFS3");
        bond.setShortName("ОФЗ 26238");

        scorer.score(bond, 1);

        assertThat(bond.getReliabilityScore()).isEqualTo(10); // 8 (листинг 1) + 2 (ОФЗ)
        assertThat(bond.isInvestmentGrade()).isTrue();
        assertThat(bond.getIssuerRating()).isEqualTo("ОФЗ");
    }

    @Test
    void thirdLevelCorporateBondIsBelowInvestmentGrade() {
        Bond bond = new Bond();
        bond.setSecId("RU000AWANTED");
        bond.setShortName("Рога и Копыта");

        scorer.score(bond, 3);

        assertThat(bond.getReliabilityScore()).isEqualTo(2);
        assertThat(bond.isInvestmentGrade()).isFalse();
        assertThat(bond.getIssuerRating()).isEqualTo("Листинг 3");
    }

    @Test
    void unknownListingLevelGetsBaseScore() {
        Bond bond = new Bond();
        bond.setSecId("RU000ANOLVL");

        scorer.score(bond, 0);

        assertThat(bond.getReliabilityScore()).isEqualTo(3);
        assertThat(bond.getIssuerRating()).isEqualTo("—");
    }
}
