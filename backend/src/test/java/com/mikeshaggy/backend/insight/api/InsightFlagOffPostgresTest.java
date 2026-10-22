package com.mikeshaggy.backend.insight.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Flag off against a real PostgreSQL, in a throw-away schema ({@code insights_flag_off}, created here, tables built by
 * Hibernate, {@code insights} dropped, schema removed afterwards) — the database's own schemas are never touched.
 * Opt-in via {@code SAVIX_PG_TEST_URL}.
 */
@EnabledIfEnvironmentVariable(named = "SAVIX_PG_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "app.features.insights-v2=false",
        "app.features.pay-cycle-v2=true"
})
@AutoConfigureMockMvc(addFilters = false)
@Import(AbstractInsightFlagOffIntegrationTest.CountingDetector.class)
class InsightFlagOffPostgresTest extends AbstractInsightFlagOffIntegrationTest {

    private static final String SCHEMA = "insights_flag_off";

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        String url = System.getenv("SAVIX_PG_TEST_URL");
        registry.add("spring.datasource.url", () -> {
            execute("drop schema if exists " + SCHEMA + " cascade");
            execute("create schema " + SCHEMA);
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
        });
        registry.add("spring.datasource.username", () -> user());
        registry.add("spring.datasource.password", () -> password());
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @AfterAll
    static void dropSchema() {
        execute("drop schema if exists " + SCHEMA + " cascade");
    }

    private static String user() {
        return System.getenv().getOrDefault("SAVIX_PG_TEST_USER", "admin");
    }

    private static String password() {
        return System.getenv().getOrDefault("SAVIX_PG_TEST_PASSWORD", "");
    }

    private static void execute(String sql) {
        try (Connection c = DriverManager.getConnection(System.getenv("SAVIX_PG_TEST_URL"), user(), password());
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
