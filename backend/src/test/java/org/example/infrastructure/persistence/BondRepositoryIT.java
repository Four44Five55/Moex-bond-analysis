package org.example.infrastructure.persistence;

import org.example.domain.filter.BondSpecifications;
import org.example.domain.filter.FilterCriteria;
import org.example.domain.model.Bond;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест: реальный PostgreSQL в Testcontainers, миграции Flyway,
 * проверка сохранения облигаций и работы Specification-фильтров.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class BondRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private BondRepository bondRepository;

    private Bond bond(String secId, double yield, int reliability, boolean ig, boolean suspicious) {
        Bond b = new Bond();
        b.setSecId(secId);
        b.setShortName(secId);
        b.setFaceValue(1000);
        b.setYieldToMaturity(yield);
        b.setDuration(2.0);
        b.setReliabilityScore(reliability);
        b.setInvestmentGrade(ig);
        b.setSuspiciousYield(suspicious);
        b.setMaturityDate(LocalDate.now().plusYears(3));
        return b;
    }

    @Test
    void savesAndFiltersByCriteria() {
        bondRepository.saveAll(List.of(
                bond("SU-IG-HIGH", 15.0, 9, true, false),
                bond("CORP-LOW", 6.0, 4, false, false),
                bond("JUNK-SUSPICIOUS", 500.0, 8, true, true)
        ));

        FilterCriteria criteria = FilterCriteria.builder()
                .minYield(10.0)
                .minReliabilityScore(7)
                .onlyInvestmentGrade(true)
                .excludeSuspicious(true)
                .build();

        List<Bond> result = bondRepository.findAll(BondSpecifications.fromCriteria(criteria));

        assertThat(result).extracting(Bond::getSecId).containsExactly("SU-IG-HIGH");
    }

    @Test
    void findBySecIdReturnsPersistedBond() {
        bondRepository.save(bond("RU000TEST", 12.0, 7, true, false));
        assertThat(bondRepository.findBySecId("RU000TEST")).isPresent();
    }
}
