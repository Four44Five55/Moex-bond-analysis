package org.example.domain.analysis;

import org.example.domain.model.Bond;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BondAnalyzerTest {

    private final BondAnalyzer analyzer = new BondAnalyzer();

    private Bond bond(double yield, int reliability, boolean ig) {
        Bond b = new Bond();
        b.setYieldToMaturity(yield);
        b.setReliabilityScore(reliability);
        b.setInvestmentGrade(ig);
        return b;
    }

    @Test
    void computesAggregatesOverBonds() {
        List<Bond> bonds = List.of(
                bond(10.0, 8, true),
                bond(20.0, 6, false),
                bond(30.0, 10, true)
        );

        assertThat(analyzer.calculateAverageYield(bonds)).isEqualTo(20.0);
        assertThat(analyzer.calculateAverageReliability(bonds)).isEqualTo(8.0);
        assertThat(analyzer.investmentGradeShare(bonds)).isEqualTo(2.0 / 3.0);
        assertThat(analyzer.maxYield(bonds)).isEqualTo(30.0);
    }

    @Test
    void handlesEmptyListGracefully() {
        assertThat(analyzer.calculateAverageYield(List.of())).isZero();
        assertThat(analyzer.investmentGradeShare(List.of())).isZero();
        assertThat(analyzer.maxYield(List.of())).isZero();
    }
}
