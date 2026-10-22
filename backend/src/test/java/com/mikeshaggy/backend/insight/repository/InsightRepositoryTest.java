package com.mikeshaggy.backend.insight.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.insight.service.InsightService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Stage 7.1 mapping and queries on H2 (the schema Hibernate derives from the entity, incl. the named unique
 * constraint). PostgreSQL-specific behaviour (jsonb, the partial index, the real DDL) is covered by
 * {@code InsightPostgresTest}.
 */
@DataJpaTest
class InsightRepositoryTest {

    private static final LocalDate CYCLE = LocalDate.of(2026, 10, 9);
    private static final LocalDate PREVIOUS_CYCLE = LocalDate.of(2026, 9, 9);

    @Autowired
    private InsightRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = user("insights@example.com");
        wallet = wallet(user, "Daily");
    }

    @Test
    void mapsEveryColumn_andAssignsIdAndCreatedAt() {
        Insight saved = repository.saveAndFlush(Insight.builder()
                .userId(user.getId()).walletId(wallet.getId())
                .type(InsightType.ONE_OFF_SKEWING_FORECAST).severity(InsightSeverity.ALERT).cycleStart(CYCLE)
                .dedupeKey("tx:42").title("A one-off skews the forecast").description("300.00 on 12 Oct")
                .payload("{\"impact\":300.00,\"transactionId\":42}")
                .actionType(InsightActionType.EXCLUDE_TRANSACTION).actionRef("42")
                .build());
        entityManager.clear();

        Insight read = repository.findById(saved.getId()).orElseThrow();

        assertThat(read.getId()).isNotNull();
        assertThat(read.getUserId()).isEqualTo(user.getId());
        assertThat(read.getWalletId()).isEqualTo(wallet.getId());
        assertThat(read.getType()).isEqualTo(InsightType.ONE_OFF_SKEWING_FORECAST);
        assertThat(read.getSeverity()).isEqualTo(InsightSeverity.ALERT);
        assertThat(read.getCycleStart()).isEqualTo(CYCLE);
        assertThat(read.getDedupeKey()).isEqualTo("tx:42");
        assertThat(read.getTitle()).isEqualTo("A one-off skews the forecast");
        assertThat(read.getDescription()).isEqualTo("300.00 on 12 Oct");
        assertThat(read.getPayload()).contains("\"transactionId\"").contains("42");
        assertThat(read.getActionType()).isEqualTo(InsightActionType.EXCLUDE_TRANSACTION);
        assertThat(read.getActionRef()).isEqualTo("42");
        assertThat(read.getCreatedAt()).isNotNull();
        assertThat(read.getDismissedAt()).isNull();
        assertThat(read.isDismissed()).isFalse();
    }

    @Test
    void nullOptionalColumns_areAllowed() {
        Insight saved = repository.saveAndFlush(insight(InsightType.SALARY_NOT_RECEIVED, "salary", CYCLE));
        entityManager.clear();

        Insight read = repository.findById(saved.getId()).orElseThrow();

        assertThat(read.getPayload()).isNull();
        assertThat(read.getDescription()).isNull();
        assertThat(read.getActionType()).isNull();
        assertThat(read.getActionRef()).isNull();
    }

    @Test
    void uniqueConstraint_rejectsSameWalletTypeCycleAndKey_asUniqueViolation() {
        repository.saveAndFlush(insight(InsightType.CATEGORY_ABOVE_TYPICAL, "category:7", CYCLE));

        assertThatThrownBy(() -> repository.saveAndFlush(
                insight(InsightType.CATEGORY_ABOVE_TYPICAL, "category:7", CYCLE)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(InsightService.isUniqueViolation(e)).isTrue());
    }

    @Test
    void uniqueConstraint_allowsAnyOtherCombination() {
        repository.saveAndFlush(insight(InsightType.CATEGORY_ABOVE_TYPICAL, "category:7", CYCLE));
        repository.saveAndFlush(insight(InsightType.CATEGORY_ABOVE_TYPICAL, "category:8", CYCLE));       // other key
        repository.saveAndFlush(insight(InsightType.HIGH_IMPULSE_SPENDING, "category:7", CYCLE));       // other type
        repository.saveAndFlush(insight(InsightType.CATEGORY_ABOVE_TYPICAL, "category:7", PREVIOUS_CYCLE)); // other cycle
        Wallet second = wallet(user, "Second");
        repository.saveAndFlush(Insight.builder().userId(user.getId()).walletId(second.getId())
                .type(InsightType.CATEGORY_ABOVE_TYPICAL).severity(InsightSeverity.WARN).cycleStart(CYCLE)
                .dedupeKey("category:7").title("t").build());                                            // other wallet

        assertThat(repository.count()).isEqualTo(5);
    }

    @Test
    void findActive_excludesDismissed_otherCycles_otherWallets_andOtherUsers() {
        Insight active = repository.save(insight(InsightType.CATEGORY_ABOVE_TYPICAL, "a", CYCLE));
        Insight dismissed = insight(InsightType.CYCLE_CLOSED, "b", CYCLE);
        dismissed.setDismissedAt(Instant.parse("2026-10-20T10:00:00Z"));
        repository.save(dismissed);
        repository.save(insight(InsightType.CATEGORY_ABOVE_TYPICAL, "a", PREVIOUS_CYCLE));
        Wallet second = wallet(user, "Second");
        repository.save(Insight.builder().userId(user.getId()).walletId(second.getId())
                .type(InsightType.CYCLE_CLOSED).severity(InsightSeverity.INFO).cycleStart(CYCLE)
                .dedupeKey("x").title("t").build());
        Insight later = repository.save(insight(InsightType.SALARY_NOT_RECEIVED, "c", CYCLE));
        repository.flush();
        entityManager.clear();

        List<Insight> rows = repository.findActiveByWalletAndCycle(user.getId(), wallet.getId(), CYCLE);

        assertThat(rows).extracting(Insight::getId).containsExactly(active.getId(), later.getId());
        User other = user("other-insights@example.com");
        assertThat(repository.findActiveByWalletAndCycle(other.getId(), wallet.getId(), CYCLE)).isEmpty();
    }

    @Test
    void exists_countsDismissedRows_andIsScopedToTheFullKey() {
        Insight dismissed = insight(InsightType.CATEGORY_ABOVE_TYPICAL, "category:7", CYCLE);
        dismissed.setDismissedAt(Instant.parse("2026-10-20T10:00:00Z"));
        repository.saveAndFlush(dismissed);

        assertThat(repository.existsByWalletTypeCycleAndKey(wallet.getId(), InsightType.CATEGORY_ABOVE_TYPICAL, CYCLE, "category:7")).isTrue();
        assertThat(repository.existsByWalletTypeCycleAndKey(wallet.getId(), InsightType.CATEGORY_ABOVE_TYPICAL, CYCLE, "category:8")).isFalse();
        assertThat(repository.existsByWalletTypeCycleAndKey(wallet.getId(), InsightType.HIGH_IMPULSE_SPENDING, CYCLE, "category:7")).isFalse();
        assertThat(repository.existsByWalletTypeCycleAndKey(wallet.getId(), InsightType.CATEGORY_ABOVE_TYPICAL, PREVIOUS_CYCLE, "category:7")).isFalse();
        assertThat(repository.existsByWalletTypeCycleAndKey(wallet.getId() + 1000, InsightType.CATEGORY_ABOVE_TYPICAL, CYCLE, "category:7")).isFalse();
    }

    @Test
    void dismiss_setsTimestampOnce_onlyForTheOwner() {
        Insight saved = repository.saveAndFlush(insight(InsightType.CYCLE_CLOSED, "cycle", CYCLE));
        Instant first = Instant.parse("2026-10-20T10:00:00Z");
        Instant second = Instant.parse("2026-10-21T10:00:00Z");
        UUID stranger = UUID.randomUUID();

        assertThat(repository.dismiss(saved.getId(), stranger, first)).isZero();
        assertThat(repository.dismiss(saved.getId(), user.getId(), first)).isEqualTo(1);
        assertThat(repository.dismiss(saved.getId(), user.getId(), second)).isZero();
        assertThat(repository.dismiss(saved.getId() + 1000, user.getId(), second)).isZero();

        Insight read = repository.findById(saved.getId()).orElseThrow();
        assertThat(read.getDismissedAt()).isEqualTo(first);
        assertThat(repository.findByIdAndUserId(saved.getId(), user.getId())).isPresent();
        assertThat(repository.findByIdAndUserId(saved.getId(), stranger)).isEmpty();
    }

    @Test
    void jsonPayload_roundTripsAsText_withDecimalsIntact() {
        String payload = "{\"amount\":162.30,\"nested\":{\"list\":[1,2.50]},\"label\":\"Groceries ż\"}";
        Insight saved = repository.saveAndFlush(Insight.builder()
                .userId(user.getId()).walletId(wallet.getId())
                .type(InsightType.CATEGORY_ABOVE_TYPICAL).severity(InsightSeverity.WARN).cycleStart(CYCLE)
                .dedupeKey("k").title("t").payload(payload).build());
        entityManager.clear();

        String read = repository.findById(saved.getId()).orElseThrow().getPayload();

        assertThat(read).contains("162.30").contains("2.50").contains("Groceries ż");
    }

    @Test
    void existsByWalletTypeAndKey_looksAcrossAllCycles_dismissedOrNot_scopedToUserWalletTypeAndKey() {
        repository.saveAndFlush(insight(InsightType.CYCLE_CLOSED, "cycle:2026-08-10", PREVIOUS_CYCLE));
        Wallet second = wallet(user, "Second");
        User stranger = user("stranger-insights@example.com");

        assertThat(repository.existsByWalletTypeAndKey(user.getId(), wallet.getId(), InsightType.CYCLE_CLOSED, "cycle:2026-08-10")).isTrue();
        // any cycle: an older row still blocks a re-emission under a newer current cycle
        assertThat(repository.existsByWalletTypeAndKey(user.getId(), wallet.getId(), InsightType.CYCLE_CLOSED, "cycle:2026-09-09")).isFalse();
        assertThat(repository.existsByWalletTypeAndKey(user.getId(), wallet.getId(), InsightType.PRE_PAYDAY_NEGATIVE, "cycle:2026-08-10")).isFalse();
        assertThat(repository.existsByWalletTypeAndKey(user.getId(), second.getId(), InsightType.CYCLE_CLOSED, "cycle:2026-08-10")).isFalse();
        assertThat(repository.existsByWalletTypeAndKey(stranger.getId(), wallet.getId(), InsightType.CYCLE_CLOSED, "cycle:2026-08-10")).isFalse();

        Insight dismissed = insight(InsightType.PRE_PAYDAY_NEGATIVE, "cycle:2026-08-10", CYCLE);
        dismissed.setDismissedAt(Instant.parse("2026-10-20T10:00:00Z"));
        repository.saveAndFlush(dismissed);
        assertThat(repository.existsByWalletTypeAndKey(user.getId(), wallet.getId(), InsightType.PRE_PAYDAY_NEGATIVE, "cycle:2026-08-10")).isTrue();
    }

    @Test
    void existsDismissedByWalletTypeAndKey_isTrueOnlyForADismissedRow_inAnyCycle_withinTheSameScope() {
        repository.saveAndFlush(insight(InsightType.RECURRING_MERCHANT_DETECTED, "merchant:netflix", PREVIOUS_CYCLE));
        Wallet second = wallet(user, "Second");
        User stranger = user("stranger-dismissed@example.com");

        // present but not dismissed
        assertThat(repository.existsDismissedByWalletTypeAndKey(user.getId(), wallet.getId(), InsightType.RECURRING_MERCHANT_DETECTED, "merchant:netflix")).isFalse();

        Insight dismissed = insight(InsightType.RECURRING_MERCHANT_DETECTED, "merchant:spotify", PREVIOUS_CYCLE);
        dismissed.setDismissedAt(Instant.parse("2026-10-20T10:00:00Z"));
        repository.saveAndFlush(dismissed);

        assertThat(repository.existsDismissedByWalletTypeAndKey(user.getId(), wallet.getId(), InsightType.RECURRING_MERCHANT_DETECTED, "merchant:spotify")).isTrue();
        assertThat(repository.existsDismissedByWalletTypeAndKey(user.getId(), wallet.getId(), InsightType.RECURRING_MERCHANT_DETECTED, "merchant:other")).isFalse();
        assertThat(repository.existsDismissedByWalletTypeAndKey(user.getId(), wallet.getId(), InsightType.CATEGORY_ABOVE_TYPICAL, "merchant:spotify")).isFalse();
        assertThat(repository.existsDismissedByWalletTypeAndKey(user.getId(), second.getId(), InsightType.RECURRING_MERCHANT_DETECTED, "merchant:spotify")).isFalse();
        assertThat(repository.existsDismissedByWalletTypeAndKey(stranger.getId(), wallet.getId(), InsightType.RECURRING_MERCHANT_DETECTED, "merchant:spotify")).isFalse();
    }

    private Insight insight(InsightType type, String key, LocalDate cycleStart) {
        return Insight.builder()
                .userId(user.getId())
                .walletId(wallet.getId())
                .type(type)
                .severity(InsightSeverity.WARN)
                .cycleStart(cycleStart)
                .dedupeKey(key)
                .title(type + " " + key)
                .build();
    }

    private User user(String email) {
        return entityManager.persistFlushFind(User.builder()
                .email(email)
                .username(email.substring(0, email.indexOf('@')))
                .passwordHash("hash")
                .build());
    }

    private Wallet wallet(User owner, String name) {
        return entityManager.persistFlushFind(Wallet.builder()
                .user(owner)
                .name(name)
                .balance(new BigDecimal("100.00"))
                .build());
    }
}
