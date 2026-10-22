package com.mikeshaggy.backend.insight.api;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@link AbstractInsightHttpIntegrationTest} against a real PostgreSQL (schema from {@code 01_init.sql} and/or
 * {@code 06_insights.sql}, Hibernate {@code validate}). Opt-in: only when {@code SAVIX_PG_TEST_URL} is set (with
 * {@code SAVIX_PG_TEST_USER} / {@code SAVIX_PG_TEST_PASSWORD}) — never the developer's data.
 */
@EnabledIfEnvironmentVariable(named = "SAVIX_PG_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "app.features.insights-v2=true",
        "app.features.pay-cycle-v2=true"
})
@AutoConfigureMockMvc(addFilters = false)
@Import(AbstractInsightHttpIntegrationTest.HttpDetectors.class)
class InsightHttpPostgresTest extends AbstractInsightHttpIntegrationTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("SAVIX_PG_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("SAVIX_PG_TEST_USER", "admin"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("SAVIX_PG_TEST_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
