package com.mikeshaggy.backend.insight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link AbstractInsightEvaluationIntegrationTest} plus PostgreSQL-only checks, against a real PostgreSQL whose
 * schema was created from {@code 01_init.sql} (and/or upgraded with {@code 06_insights.sql}) — never the
 * developer's data. Opt-in: runs only when {@code SAVIX_PG_TEST_URL} is set (with {@code SAVIX_PG_TEST_USER} /
 * {@code SAVIX_PG_TEST_PASSWORD}); Hibernate validates the entity mapping against that schema
 * ({@code ddl-auto=validate}) and never changes it.
 */
@EnabledIfEnvironmentVariable(named = "SAVIX_PG_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "app.features.insights-v2=true",
        "app.features.pay-cycle-v2=true"
})
@Import(AbstractInsightEvaluationIntegrationTest.Detectors.class)
class InsightEvaluationPostgresTest extends AbstractInsightEvaluationIntegrationTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("SAVIX_PG_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("SAVIX_PG_TEST_USER", "admin"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("SAVIX_PG_TEST_PASSWORD", ""));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void payload_isStoredAsJsonb_withNumericValuesExact() {
        RULE.set(ctx -> Optional.of(new com.mikeshaggy.backend.insight.detector.InsightCandidate(
                InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "category:3", "t", null,
                java.util.Map.of("typical", new java.math.BigDecimal("162.30")), null, null)));
        insightService.evaluate(wallet.getId(), user.getId());

        String type = jdbcTemplate.queryForObject(
                "select pg_typeof(payload)::text from insights where user_id = ?", String.class, user.getId());
        String typical = jdbcTemplate.queryForObject(
                "select payload->>'typical' from insights where user_id = ?", String.class, user.getId());
        String numericType = jdbcTemplate.queryForObject(
                "select jsonb_typeof(payload->'typical') from insights where user_id = ?", String.class, user.getId());

        assertThat(type).isEqualTo("jsonb");
        assertThat(typical).isEqualTo("162.30");
        assertThat(numericType).isEqualTo("number");
    }

    @Test
    void uniqueConstraint_isEnforcedByPostgres_withSqlState23505() {
        Insight first = insight("category:3", START);
        insightRepository.saveAndFlush(first);

        assertThatThrownBy(() -> insightRepository.saveAndFlush(insight("category:3", START)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(InsightService.isUniqueViolation(e)).isTrue())
                .hasMessageContaining("uq_insights_wallet_type_cycle_key");
    }

    @Test
    void severityCheck_isNotMistakenForADuplicate() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into insights (user_id, wallet_id, type, severity, cycle_start, dedupe_key, title) "
                        + "values (?, ?, 'CYCLE_CLOSED', 'LOUD', ?, 'k', 't')",
                user.getId(), wallet.getId(), START))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(InsightService.isUniqueViolation(e)).isFalse());
    }

    @Test
    void foreignKeys_rejectUnknownWallet_andCascadeOnWalletDelete() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into insights (user_id, wallet_id, type, severity, cycle_start, dedupe_key, title) "
                        + "values (?, ?, 'CYCLE_CLOSED', 'INFO', ?, 'k', 't')",
                user.getId(), Integer.MAX_VALUE, START))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(InsightService.isUniqueViolation(e)).isFalse());

        insightRepository.saveAndFlush(insight("category:3", START));
        jdbcTemplate.update("delete from wallets where id = ?", wallet.getId());

        assertThat(jdbcTemplate.queryForObject("select count(*) from insights where wallet_id = ?", Integer.class,
                wallet.getId())).isZero();
    }

    @Test
    void activeLookup_canUseThePartialIndex() {
        insightRepository.saveAndFlush(insight("category:3", START));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        List<String> plan = tx.execute(status -> {
            jdbcTemplate.execute("set local enable_seqscan = off");
            return jdbcTemplate.queryForList("explain select * from insights where wallet_id = "
                    + wallet.getId() + " and cycle_start = date '" + START + "' and dismissed_at is null", String.class);
        });

        assertThat(String.join("\n", plan)).contains("idx_insights_wallet_cycle");
    }

    private Insight insight(String key, LocalDate cycleStart) {
        return Insight.builder()
                .userId(user.getId()).walletId(wallet.getId())
                .type(InsightType.CATEGORY_ABOVE_TYPICAL).severity(InsightSeverity.WARN)
                .cycleStart(cycleStart).dedupeKey(key).title("t")
                .build();
    }
}
