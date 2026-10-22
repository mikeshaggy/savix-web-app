package com.mikeshaggy.backend.insight.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@link AbstractInsightDetectorsIntegrationTest} on a real PostgreSQL whose schema comes from {@code 01_init.sql}
 * (and/or the upgrade path through {@code 06_insights.sql}) — never the developer's data. Opt-in through
 * {@code SAVIX_PG_TEST_URL} / {@code _USER} / {@code _PASSWORD}; {@code ddl-auto=validate} never changes the schema.
 * Proves the Stage 7.3 queries (per-category pace-eligible series, importance split, cross-cycle insight lookups) and
 * the jsonb payloads on PostgreSQL.
 */
@EnabledIfEnvironmentVariable(named = "SAVIX_PG_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "app.features.insights-v2=true",
        "app.features.pay-cycle-v2=true",
        "app.features.forecast-v2=true"
})
@Import(AbstractInsightDetectorsIntegrationTest.ControlledClock.class)
class InsightDetectorsPostgresTest extends AbstractInsightDetectorsIntegrationTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("SAVIX_PG_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("SAVIX_PG_TEST_USER", "admin"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("SAVIX_PG_TEST_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
