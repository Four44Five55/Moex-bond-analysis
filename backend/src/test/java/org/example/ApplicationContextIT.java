package org.example;

import org.example.application.PortfolioService;
import org.example.domain.portfolio.allocation.AllocationDimensionFactory;
import org.example.domain.portfolio.history.PortfolioValueCalculator;
import org.example.domain.portfolio.history.XirrCalculator;
import org.example.web.v1.PortfolioController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Поднимает полный контекст приложения на реальном PostgreSQL: остальные тесты — модульные
 * либо {@code @DataJpaTest}, и связность бинов не проверял никто. Ловит рассинхрон конструкторов,
 * недостающие {@code @Component} и циклы между сервисами.
 * <p>
 * Синхронизация с MOEX выключена (иначе {@code ApplicationRunner} полез бы в сеть),
 * кеш — {@code none}, чтобы тест не требовал живого Redis.
 */
@SpringBootTest(properties = {
        "moex.sync.enabled=false",
        "spring.cache.type=none"
})
@Testcontainers
class ApplicationContextIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private PortfolioController portfolioController;

    @Autowired
    private XirrCalculator xirrCalculator;

    @Autowired
    private PortfolioValueCalculator portfolioValueCalculator;

    @Autowired
    private AllocationDimensionFactory allocationDimensionFactory;

    @Test
    void contextLoadsWithPortfolioAnalytics() {
        assertThat(portfolioService).isNotNull();
        assertThat(portfolioController).isNotNull();
        assertThat(xirrCalculator).isNotNull();
        assertThat(portfolioValueCalculator).isNotNull();
    }

    @Test
    void allocationDimensionsAreDiscoveredAsBeans() {
        // Фабрика собирает измерения списком — новое измерение не должно требовать правок фабрики
        assertThat(allocationDimensionFactory.forKey("type")).isNotNull();
        assertThat(allocationDimensionFactory.forKey("sector")).isNotNull();
        assertThat(allocationDimensionFactory.forKey("currency")).isNotNull();
    }
}
