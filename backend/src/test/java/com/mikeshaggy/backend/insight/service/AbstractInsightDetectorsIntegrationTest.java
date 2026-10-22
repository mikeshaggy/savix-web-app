package com.mikeshaggy.backend.insight.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.repository.CategoryRepository;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightKind;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.insight.dto.InsightAvailability;
import com.mikeshaggy.backend.insight.dto.InsightListResponse;
import com.mikeshaggy.backend.insight.dto.InsightResponse;
import com.mikeshaggy.backend.insight.repository.InsightRepository;
import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import com.mikeshaggy.backend.ledger.repository.WalletEntryRepository;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.transfer.repository.TransferRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.repository.UserRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Stage 7.3 end to end: the ten real detectors, every canonical service and the real {@link PayCycleService} over a
 * synthetic copy of the audited Sep 14 situation (sanitized {@code FX_*} titles; the numbers of
 * {@code September2026Fixture}), with only the clock controlled. Covers the acceptance list (at most the four
 * audited insights, plus the informational cycle summary), stability, exclusion, cycle rollover and anchor
 * correction, late salary with unrelated income, recurring merchants, isolation and concurrency. Subclasses run it on
 * H2 and on PostgreSQL.
 */
@ExtendWith(OutputCaptureExtension.class)
abstract class AbstractInsightDetectorsIntegrationTest {

    static final LocalDate AUDIT_DAY = LocalDate.of(2026, 9, 14);
    static final List<LocalDate> ANCHORS = List.of(
            LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 10),
            LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 9));
    /** Day-3 groceries of the closed cycles, newest first: median of the last six = 162.34 (mean 167.45). */
    static final List<String> GROCERIES_DAY3 = List.of("150.00", "160.00", "162.00", "162.68", "170.00", "200.00",
            "999.00");

    /** The application clock, moved by the tests (Clock beans read it on every call). */
    static final MutableClock CLOCK = new MutableClock();

    @TestConfiguration
    static class ControlledClock {
        @Bean
        @Primary
        Clock controlledClock() {
            return CLOCK;
        }
    }

    @Autowired
    InsightService insightService;
    @Autowired
    InsightRepository insightRepository;
    @Autowired
    PayCycleService payCycleService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WalletRepository walletRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    TransferRepository transferRepository;
    @Autowired
    WalletEntryRepository walletEntryRepository;
    @Autowired
    FixedPaymentRepository fixedPaymentRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    InsightWriter insightWriter;

    private final List<UUID> users = new ArrayList<>();

    @BeforeEach
    void atTheAuditDay() {
        CLOCK.set(AUDIT_DAY);
    }

    @AfterEach
    void cleanUp() {
        for (UUID user : users) {
            String wallets = "(select id from wallets where user_id = ?)";
            jdbcTemplate.update("delete from insights where user_id = ?", user);
            jdbcTemplate.update("delete from wallet_entries where wallet_id in " + wallets, user);
            jdbcTemplate.update("delete from transfers where from_wallet_id in " + wallets
                    + " or to_wallet_id in " + wallets, user, user);
            jdbcTemplate.update("delete from fixed_payment_occurrences where fixed_payment_id in "
                    + "(select id from fixed_payments where wallet_id in " + wallets + ")", user);
            jdbcTemplate.update("delete from fixed_payments where wallet_id in " + wallets, user);
            jdbcTemplate.update("delete from transactions where wallet_id in " + wallets, user);
            jdbcTemplate.update("update users set salary_wallet_id = null where id = ?", user);
            jdbcTemplate.update("delete from categories where user_id = ?", user);
            jdbcTemplate.update("delete from wallets where user_id = ?", user);
            jdbcTemplate.update("delete from users where id = ?", user);
        }
        users.clear();
        CLOCK.set(AUDIT_DAY);
    }

    // --- the audited day ------------------------------------------------------------------------------------------

    @Test
    void auditedSep14_showsAtMostTheFourAuditedInsights_plusTheCycleSummary(CapturedOutput output) {
        Fixture fixture = fixture();

        InsightListResponse response = insightService.evaluate(fixture.daily.getId(), fixture.user.getId());

        assertThat(response.status()).isEqualTo(InsightAvailability.AVAILABLE);
        assertThat(response.cycleStart()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(response.cycleState()).isEqualTo(CycleState.OPEN);
        Map<InsightType, InsightResponse> byType = response.insights().stream()
                .collect(Collectors.toMap(InsightResponse::type, insight -> insight));
        assertThat(byType.keySet()).containsExactlyInAnyOrder(
                InsightType.CATEGORY_ABOVE_TYPICAL,     // groceries only: not smoking, not transport
                InsightType.SAVINGS_FUNDING_DAILY,
                InsightType.PRE_PAYDAY_NEGATIVE,
                InsightType.ONE_OFF_SKEWING_FORECAST,
                InsightType.CYCLE_CLOSED);              // informational summary of Aug 10 – Sep 8

        InsightResponse groceries = byType.get(InsightType.CATEGORY_ABOVE_TYPICAL);
        assertThat(groceries.actionRef()).isEqualTo("/analytics/breakdown?category=" + fixture.groceries.getId());
        assertThat(groceries.payload().get("current").decimalValue()).isEqualByComparingTo("314.84");
        assertThat(groceries.payload().get("typical").decimalValue()).isEqualByComparingTo("162.34");

        InsightResponse savings = byType.get(InsightType.SAVINGS_FUNDING_DAILY);
        assertThat(savings.payload().get("netInflow").decimalValue()).isEqualByComparingTo("3001.00");
        assertThat(savings.payload().get("savingsBalance").decimalValue()).isEqualByComparingTo("460.01");

        InsightResponse negative = byType.get(InsightType.PRE_PAYDAY_NEGATIVE);
        assertThat(negative.payload().get("minBalance").decimalValue()).isEqualByComparingTo("-93.29");
        assertThat(negative.payload().get("cycleStart").asText()).isEqualTo("2026-08-10");

        InsightResponse oneOff = byType.get(InsightType.ONE_OFF_SKEWING_FORECAST);
        assertThat(oneOff.actionType()).isEqualTo(InsightActionType.EXCLUDE_TRANSACTION);
        assertThat(oneOff.actionRef()).isEqualTo(fixture.repayment.getId().toString());
        assertThat(oneOff.payload().get("impactOnExpectedEndBalance").decimalValue())
                .isGreaterThanOrEqualTo(new BigDecimal("200.00"));

        InsightResponse closed = byType.get(InsightType.CYCLE_CLOSED);
        assertThat(closed.payload().get("cycleStart").asText()).isEqualTo("2026-08-10");
        assertThat(closed.payload().get("cycleEnd").asText()).isEqualTo("2026-09-08");
        assertThat(closed.payload().get("endBalance").decimalValue()).isEqualByComparingTo("50.00");

        // no legacy wording, no percentage on a small base, and no detector silently failed
        assertThat(response.insights()).extracting(InsightResponse::title)
                .noneMatch(title -> title.contains("770") || title.toLowerCase().contains("safe-to-spend"));
        assertThat(output).doesNotContain("insight.detector.failed").doesNotContain("insight.detector.invalid");
    }

    @Test
    void repeatedEvaluations_areStable_andStoreNothingNew() {
        Fixture fixture = fixture();

        List<Long> first = ids(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()));
        List<Long> second = ids(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()));

        assertThat(second).isEqualTo(first);
        assertThat(rows(fixture.user)).hasSize(first.size());
    }

    @Test
    void excludingTheOneOff_removesItsInsight_withoutDeletingOrDismissingTheRow() {
        Fixture fixture = fixture();
        insightService.evaluate(fixture.daily.getId(), fixture.user.getId());

        Transaction repayment = transactionRepository.findById(fixture.repayment.getId()).orElseThrow();
        repayment.setExcludedFromPace(true);
        transactionRepository.save(repayment);
        InsightListResponse after = insightService.evaluate(fixture.daily.getId(), fixture.user.getId());

        assertThat(after.insights()).extracting(InsightResponse::type)
                .doesNotContain(InsightType.ONE_OFF_SKEWING_FORECAST);
        assertThat(rows(fixture.user)).filteredOn(row -> row.getType() == InsightType.ONE_OFF_SKEWING_FORECAST)
                .singleElement().satisfies(row -> {
                    assertThat(row.getDedupeKey()).isEqualTo("transaction:" + fixture.repayment.getId());
                    assertThat(row.getDismissedAt()).isNull();
                });
    }

    // --- cycle transitions -----------------------------------------------------------------------------------------

    @Test
    void cycleClosed_isEmittedExactlyOncePerClosedCycle_acrossRolloverAndAnchorCorrection() {
        Fixture fixture = fixture();
        insightService.evaluate(fixture.daily.getId(), fixture.user.getId());
        insightService.evaluate(fixture.daily.getId(), fixture.user.getId());

        // the October salary arrives on Oct 9; evaluated on Oct 12
        CLOCK.set(LocalDate.of(2026, 10, 12));
        Transaction october = salary(fixture, LocalDate.of(2026, 10, 9));
        InsightListResponse inOctober = insightService.evaluate(fixture.daily.getId(), fixture.user.getId());
        insightService.evaluate(fixture.daily.getId(), fixture.user.getId());

        assertThat(inOctober.cycleStart()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(inOctober.insights()).filteredOn(i -> i.type() == InsightType.CYCLE_CLOSED).singleElement()
                .satisfies(insight -> assertThat(insight.payload().get("cycleStart").asText()).isEqualTo("2026-09-09"));
        assertThat(cycleClosedKeys(fixture)).containsExactly("cycle:2026-08-10", "cycle:2026-09-09");

        // the salary booking is corrected away: the cycle reverts to Sep 9 (now awaiting its salary) and the
        // August summary is not emitted a second time
        transactionRepository.delete(october);
        InsightListResponse reverted = insightService.evaluate(fixture.daily.getId(), fixture.user.getId());

        assertThat(reverted.cycleStart()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(reverted.cycleState()).isEqualTo(CycleState.AWAITING_SALARY);
        assertThat(cycleClosedKeys(fixture)).containsExactly("cycle:2026-08-10", "cycle:2026-09-09");
        assertThat(rows(fixture.user)).filteredOn(row -> row.getType() == InsightType.PRE_PAYDAY_NEGATIVE)
                .hasSize(1);
    }

    @Test
    void salaryNotReceived_atExpectedPlusFive_andUnrelatedIncomeDoesNotEndTheWait() {
        Fixture fixture = fixture();
        PayCycle cycle = payCycleService.current(fixture.user.getId()).orElseThrow();
        LocalDate expected = cycle.expectedNextAnchor();
        income(fixture, fixture.refund, expected.plusDays(1), "2500.00");

        CLOCK.set(expected.plusDays(4));
        assertThat(types(insightService.evaluate(fixture.daily.getId(), fixture.user.getId())))
                .doesNotContain(InsightType.SALARY_NOT_RECEIVED);

        CLOCK.set(expected.plusDays(5));
        InsightListResponse late = insightService.evaluate(fixture.daily.getId(), fixture.user.getId());

        assertThat(late.cycleState()).isEqualTo(CycleState.AWAITING_SALARY);
        assertThat(late.insights()).filteredOn(i -> i.type() == InsightType.SALARY_NOT_RECEIVED).singleElement()
                .satisfies(insight -> {
                    assertThat(insight.actionType()).isNull();
                    assertThat(insight.payload().get("expectedPayday").asText()).isEqualTo(expected.toString());
                });
    }

    // --- recurring merchants --------------------------------------------------------------------------------------

    @Test
    void recurringMerchant_isSuggested_thenHiddenOnceSetUpAsAFixedPayment() {
        Fixture fixture = fixture();
        expense(fixture, fixture.subscriptions, "FX_STREAM", LocalDate.of(2026, 7, 12), "49.99", false);
        expense(fixture, fixture.subscriptions, "FX_STREAM", LocalDate.of(2026, 8, 12), "49.99", false);
        expense(fixture, fixture.subscriptions, " fx_Stream", LocalDate.of(2026, 9, 11), "49.99", false);

        InsightResponse suggestion = insightService.evaluate(fixture.daily.getId(), fixture.user.getId()).insights()
                .stream().filter(i -> i.type() == InsightType.RECURRING_MERCHANT_DETECTED).findFirst().orElseThrow();
        assertThat(suggestion.actionType()).isEqualTo(InsightActionType.CREATE_FIXED_PAYMENT);
        assertThat(suggestion.payload().get("prefill").get("categoryId").asInt())
                .isEqualTo(fixture.subscriptions.getId());

        fixedPaymentRepository.save(FixedPayment.builder().wallet(fixture.daily).category(fixture.subscriptions)
                .title("FX_STREAM").amount(new BigDecimal("49.99")).anchorDate(LocalDate.of(2026, 10, 11))
                .cycle(Cycle.MONTHLY).activeFrom(LocalDate.of(2026, 10, 11)).build());

        assertThat(types(insightService.evaluate(fixture.daily.getId(), fixture.user.getId())))
                .doesNotContain(InsightType.RECURRING_MERCHANT_DETECTED);
        assertThat(rows(fixture.user)).filteredOn(row -> row.getType() == InsightType.RECURRING_MERCHANT_DETECTED)
                .hasSize(1);
    }

    // --- refresh of condition insights (Session X.1) ---------------------------------------------------------------

    @Test
    void conditionInsight_isRefreshedInPlace_whenItsFiguresChange() {
        Fixture fixture = fixture();
        InsightResponse before = byType(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()),
                InsightType.CATEGORY_ABOVE_TYPICAL);

        expense(fixture, fixture.groceries, "FX_GROCER_D", AUDIT_DAY, "50.00", false);
        InsightResponse after = byType(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()),
                InsightType.CATEGORY_ABOVE_TYPICAL);

        assertThat(after.id()).isEqualTo(before.id());
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(before.payload().get("current").decimalValue()).isEqualByComparingTo("314.84");
        assertThat(after.payload().get("current").decimalValue()).isEqualByComparingTo("364.84");
        assertThat(after.payload().get("current").decimalValue().toPlainString()).isEqualTo("364.84");
        assertThat(after.description()).contains("364.84 PLN");
        assertThat(rows(fixture.user)).filteredOn(row -> row.getType() == InsightType.CATEGORY_ABOVE_TYPICAL)
                .singleElement().satisfies(row -> {
                    assertThat(row.getDedupeKey()).isEqualTo("category:" + fixture.groceries.getId());
                    assertThat(row.getDismissedAt()).isNull();
                });
    }

    @Test
    void unchangedConditions_areNeverRewritten_acrossTheRealJsonRoundTrip() {
        // jsonb reorders keys and re-renders numbers; the tree comparison must still see "no change"
        Fixture fixture = fixture();
        insightService.evaluate(fixture.daily.getId(), fixture.user.getId());
        clearInvocations(insightWriter);

        insightService.evaluate(fixture.daily.getId(), fixture.user.getId());
        insightService.evaluate(fixture.daily.getId(), fixture.user.getId());

        verify(insightWriter, never()).refresh(any(), any());
        verify(insightWriter, never()).insert(any());
    }

    @Test
    void refreshContent_updatesOnlyTheOwnersActiveRow_onTheRealDatabase() {
        Fixture fixture = fixture();
        insightService.evaluate(fixture.daily.getId(), fixture.user.getId());
        Insight groceries = rows(fixture.user).stream()
                .filter(row -> row.getType() == InsightType.CATEGORY_ABOVE_TYPICAL).findFirst().orElseThrow();
        InsightWriter.InsightContent content = new InsightWriter.InsightContent(groceries.getSeverity(), "changed",
                "changed", "{\"current\": 1.00}", groceries.getActionType(), groceries.getActionRef());

        assertThat(insightWriter.refresh(copyWithUser(groceries, UUID.randomUUID()), content)).isZero();
        insightService.dismiss(groceries.getId(), fixture.user.getId());
        assertThat(insightWriter.refresh(groceries, content)).isZero();

        Insight stored = insightRepository.findById(groceries.getId()).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo(groceries.getTitle());
        assertThat(stored.getDismissedAt()).isNotNull();
        assertThat(stored.getCreatedAt()).isEqualTo(groceries.getCreatedAt());
    }

    @Test
    void refreshContent_rewritesAnActiveRow_keepingIdentityAndCreation_onTheRealDatabase() {
        Fixture fixture = fixture();
        insightService.evaluate(fixture.daily.getId(), fixture.user.getId());
        Insight groceries = rows(fixture.user).stream()
                .filter(row -> row.getType() == InsightType.CATEGORY_ABOVE_TYPICAL).findFirst().orElseThrow();

        assertThat(insightWriter.refresh(groceries, new InsightWriter.InsightContent(groceries.getSeverity(),
                "changed", "changed", "{\"current\": 1.00}", null, null))).isEqualTo(1);

        Insight stored = insightRepository.findById(groceries.getId()).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("changed");
        assertThat(stored.getDedupeKey()).isEqualTo(groceries.getDedupeKey());
        assertThat(stored.getCycleStart()).isEqualTo(groceries.getCycleStart());
        assertThat(stored.getCreatedAt()).isEqualTo(groceries.getCreatedAt());
        assertThat(stored.getDismissedAt()).isNull();
        assertThat(stored.getActionType()).isNull();
    }

    private static Insight copyWithUser(Insight source, UUID userId) {
        return Insight.builder().id(source.getId()).userId(userId).walletId(source.getWalletId())
                .type(source.getType()).severity(source.getSeverity()).cycleStart(source.getCycleStart())
                .dedupeKey(source.getDedupeKey()).title(source.getTitle()).build();
    }

    @Test
    void dismissedCondition_isNotRewrittenNorRevived_whenItsFiguresChange() {
        Fixture fixture = fixture();
        InsightResponse groceries = byType(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()),
                InsightType.CATEGORY_ABOVE_TYPICAL);
        insightService.dismiss(groceries.id(), fixture.user.getId());

        expense(fixture, fixture.groceries, "FX_GROCER_D", AUDIT_DAY, "50.00", false);
        InsightListResponse after = insightService.evaluate(fixture.daily.getId(), fixture.user.getId());

        assertThat(types(after)).doesNotContain(InsightType.CATEGORY_ABOVE_TYPICAL);
        assertThat(rows(fixture.user)).filteredOn(row -> row.getType() == InsightType.CATEGORY_ABOVE_TYPICAL)
                .singleElement().satisfies(row -> {
                    assertThat(row.getDismissedAt()).isNotNull();
                    assertThat(row.getDescription()).contains("314.84 PLN").doesNotContain("364.84");
                });
    }

    @Test
    void eventInsight_keepsItsFirstSnapshot() {
        Fixture fixture = fixture();
        InsightResponse closed = byType(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()),
                InsightType.CYCLE_CLOSED);

        // a late-booked August expense changes the closed cycle's variable total; the summary stays as first shown
        expense(fixture, fixture.groceries, "FX_LATE", LocalDate.of(2026, 9, 1), "40.00", false);
        InsightResponse again = byType(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()),
                InsightType.CYCLE_CLOSED);

        assertThat(again.id()).isEqualTo(closed.id());
        assertThat(again.payload().get("variableSpent").decimalValue())
                .isEqualByComparingTo(closed.payload().get("variableSpent").decimalValue());
        assertThat(again.kind()).isEqualTo(InsightKind.LIFECYCLE);
    }

    @Test
    void concurrentRefreshes_keepOneRowPerEvent_withTheNewFigures() throws Exception {
        Fixture fixture = fixture();
        List<Long> first = ids(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()));
        expense(fixture, fixture.groceries, "FX_GROCER_D", AUDIT_DAY, "50.00", false);
        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);

        List<List<Long>> seen = runConcurrently(threads, () -> {
            barrier.await(20, TimeUnit.SECONDS);
            return ids(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()));
        });

        assertThat(seen).allSatisfy(ids -> assertThat(ids).isEqualTo(first));
        assertThat(rows(fixture.user)).hasSize(first.size());
        assertThat(byType(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()),
                InsightType.CATEGORY_ABOVE_TYPICAL).payload().get("current").decimalValue())
                .isEqualByComparingTo("364.84");
    }

    @Test
    void kind_separatesTheLifecycleSummaryFromTheNotableInsights() {
        Fixture fixture = fixture();

        InsightListResponse response = insightService.evaluate(fixture.daily.getId(), fixture.user.getId());

        assertThat(response.insights()).filteredOn(i -> i.kind() == InsightKind.NOTABLE).hasSize(4)
                .extracting(InsightResponse::type).doesNotContain(InsightType.CYCLE_CLOSED);
        assertThat(response.insights()).filteredOn(i -> i.kind() == InsightKind.LIFECYCLE)
                .extracting(InsightResponse::type).containsExactly(InsightType.CYCLE_CLOSED);
    }

    // --- isolation and concurrency --------------------------------------------------------------------------------

    @Test
    void usersAndWallets_areIsolated() {
        Fixture first = fixture();
        Fixture second = fixture();

        int firstCount = insightService.evaluate(first.daily.getId(), first.user.getId()).insights().size();
        int secondCount = insightService.evaluate(second.daily.getId(), second.user.getId()).insights().size();
        InsightListResponse savings = insightService.evaluate(first.savings.getId(), first.user.getId());

        assertThat(firstCount).isEqualTo(5);
        assertThat(secondCount).isEqualTo(5);
        assertThat(rows(first.user)).hasSize(5).allMatch(row -> row.getWalletId().equals(first.daily.getId()));
        assertThat(rows(second.user)).hasSize(5).allMatch(row -> row.getWalletId().equals(second.daily.getId()));
        assertThat(savings.status()).isEqualTo(InsightAvailability.NOT_SALARY_WALLET);
    }

    @Test
    void concurrentFirstEvaluations_storeEveryEventOnce() throws Exception {
        Fixture fixture = fixture();
        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);

        List<List<Long>> seen = runConcurrently(threads, () -> {
            barrier.await(20, TimeUnit.SECONDS);
            return ids(insightService.evaluate(fixture.daily.getId(), fixture.user.getId()));
        });

        assertThat(rows(fixture.user)).hasSize(5);
        assertThat(seen).allSatisfy(ids -> assertThat(ids).hasSize(5));
        assertThat(seen.stream().distinct()).hasSize(1);
    }

    // --- fixture --------------------------------------------------------------------------------------------------

    record Fixture(User user, Wallet daily, Wallet savings, Category salary, Category refund, Category groceries,
                   Category subscriptions, Transaction repayment) {
    }

    /**
     * Sep 14 audit day: eight salary anchors (Feb 10 … Sep 9), six-plus closed cycles of history, the Sep 9–14 spend
     * of the fixture (daily 94.98 / 335.64 / 361.64 / 316.76 / 443.00 / 0.00 incl. the 300 PLN repayment), the
     * savings transfers of the last two closed cycles, and a ledger whose August minimum is −93.29.
     */
    Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder().email("detectors-" + suffix + "@example.com")
                .username("detectors-" + suffix).passwordHash("hash").build());
        users.add(user.getId());
        Wallet daily = walletRepository.save(Wallet.builder().user(user).name("FX_SALARY_WALLET")
                .balance(new BigDecimal("5896.89")).build());
        Wallet savings = walletRepository.save(Wallet.builder().user(user).name("FX_SAVINGS_WALLET")
                .balance(new BigDecimal("460.01")).build());
        user.setSalaryWallet(daily);
        userRepository.save(user);

        Category salary = category(user, "salary", CategoryType.INCOME, true);
        Category refund = category(user, "refund", CategoryType.INCOME, false);
        Category groceries = category(user, "groceries", CategoryType.EXPENSE, false);
        Category smoking = category(user, "smoking", CategoryType.EXPENSE, false);
        Category transport = category(user, "transport", CategoryType.EXPENSE, false);
        Category restaurants = category(user, "restaurants", CategoryType.EXPENSE, false);
        Category misc = category(user, "misc", CategoryType.EXPENSE, false);
        Category travel = category(user, "travel", CategoryType.EXPENSE, false);
        Category impulse = category(user, "impulse", CategoryType.EXPENSE, false);
        Category subscriptions = category(user, "subscriptions", CategoryType.EXPENSE, false);
        Fixture partial = new Fixture(user, daily, savings, salary, refund, groceries, subscriptions, null);

        for (LocalDate anchor : ANCHORS) {
            income(partial, salary, anchor, "9444.81");
        }
        // closed cycles, newest first (the Sep 9 anchor opens the current one); unique titles: no recurring pattern
        List<LocalDate> closedStarts = ANCHORS.subList(0, ANCHORS.size() - 1).reversed();
        for (int i = 0; i < closedStarts.size(); i++) {
            LocalDate start = closedStarts.get(i);
            expense(partial, smoking, "FX_SMOKES_" + start, start.plusDays(1), "63.56", false);
            expense(partial, groceries, "FX_GROCER_" + start, start.plusDays(2), GROCERIES_DAY3.get(i), false);
            expense(partial, transport, "FX_TRANSIT_" + start, start.plusDays(3), "22.25", false);
            expense(partial, misc, "FX_BIG_" + start, start.plusDays(14), "3000.00", false);
        }

        // current cycle Sep 9 – 14 (day totals 94.98, 335.64, 361.64, 316.76, 443.00, 0.00)
        expense(partial, groceries, "FX_GROCER_A", LocalDate.of(2026, 9, 9), "94.98", false);
        expense(partial, groceries, "FX_GROCER_B", LocalDate.of(2026, 9, 10), "120.00", false);
        expense(partial, transport, "FX_TRAIN", LocalDate.of(2026, 9, 10), "193.62", false);
        expense(partial, smoking, "FX_SMOKES_A", LocalDate.of(2026, 9, 10), "22.02", false);
        expense(partial, groceries, "FX_GROCER_C", LocalDate.of(2026, 9, 11), "99.86", false);
        expense(partial, restaurants, "FX_DINER_A", LocalDate.of(2026, 9, 11), "130.00", false);
        expense(partial, restaurants, "FX_DINER_B", LocalDate.of(2026, 9, 11), "131.78", false);
        expense(partial, smoking, "FX_SMOKES_B", LocalDate.of(2026, 9, 12), "33.48", false);
        expense(partial, impulse, "FX_IMPULSE", LocalDate.of(2026, 9, 12), "150.00", true);
        expense(partial, travel, "FX_TRIP", LocalDate.of(2026, 9, 12), "133.28", false);
        Transaction repayment = expense(partial, misc, "FX_REPAYMENT", LocalDate.of(2026, 9, 13), "300.00", false);
        expense(partial, restaurants, "FX_DINER_C", LocalDate.of(2026, 9, 13), "143.00", false);

        // savings → daily over the last two closed cycles: in 11,351.00, out 8,350.00 (net +3,001.00)
        transfer(savings, daily, LocalDate.of(2026, 8, 20), "6100.00");
        transfer(daily, savings, LocalDate.of(2026, 8, 12), "4300.00");
        transfer(savings, daily, LocalDate.of(2026, 7, 25), "5251.00");
        transfer(daily, savings, LocalDate.of(2026, 7, 12), "4050.00");

        // ledger: the August cycle bottoms out at −93.29 before the Sep 9 salary
        entry(daily, LocalDate.of(2026, 8, 10), "2000.00");
        entry(daily, LocalDate.of(2026, 9, 5), "-93.29");
        entry(daily, LocalDate.of(2026, 9, 8), "50.00");
        entry(daily, LocalDate.of(2026, 9, 9), "9494.81");
        entry(daily, LocalDate.of(2026, 9, 14), "5896.89");

        return new Fixture(user, daily, savings, salary, refund, groceries, subscriptions, repayment);
    }

    private Category category(User user, String name, CategoryType type, boolean anchor) {
        return categoryRepository.save(Category.builder().user(user).name(name).type(type).isCycleAnchor(anchor)
                .build());
    }

    Transaction salary(Fixture fixture, LocalDate date) {
        return income(fixture, fixture.salary, date, "9444.81");
    }

    Transaction income(Fixture fixture, Category category, LocalDate date, String amount) {
        return transactionRepository.save(Transaction.builder().wallet(fixture.daily).category(category)
                .title("FX_INCOME_" + date).amount(new BigDecimal(amount)).transactionDate(date).build());
    }

    Transaction expense(Fixture fixture, Category category, String title, LocalDate date, String amount,
                        boolean shouldntHave) {
        return transactionRepository.save(Transaction.builder().wallet(fixture.daily).category(category).title(title)
                .amount(new BigDecimal(amount)).transactionDate(date)
                .importance(shouldntHave ? Importance.SHOULDNT_HAVE : Importance.ESSENTIAL).build());
    }

    private void transfer(Wallet from, Wallet to, LocalDate date, String amount) {
        transferRepository.save(Transfer.builder().fromWallet(from).toWallet(to).amount(new BigDecimal(amount))
                .transferDate(date).build());
    }

    private long nextSourceId = 1;

    private void entry(Wallet wallet, LocalDate date, String balanceAfter) {
        walletEntryRepository.save(WalletEntry.builder().wallet(wallet).amountSigned(BigDecimal.ONE)
                .balanceAfter(new BigDecimal(balanceAfter)).entryDate(date).sourceType(SourceType.ADJUSTMENT)
                .sourceId(nextSourceId++).build());
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    List<Insight> rows(User user) {
        return insightRepository.findAll().stream().filter(row -> row.getUserId().equals(user.getId())).toList();
    }

    private List<String> cycleClosedKeys(Fixture fixture) {
        return rows(fixture.user).stream().filter(row -> row.getType() == InsightType.CYCLE_CLOSED)
                .map(Insight::getDedupeKey).sorted().toList();
    }

    private static List<Long> ids(InsightListResponse response) {
        return response.insights().stream().map(InsightResponse::id).sorted().toList();
    }

    private static InsightResponse byType(InsightListResponse response, InsightType type) {
        return response.insights().stream().filter(insight -> insight.type() == type).findFirst().orElseThrow();
    }

    private static List<InsightType> types(InsightListResponse response) {
        return response.insights().stream().map(InsightResponse::type).toList();
    }

    private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(task));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    /** A clock whose day the test sets; midday UTC so that no zone shifts the date. */
    static final class MutableClock extends Clock {

        private volatile Instant instant = Instant.EPOCH;

        void set(LocalDate day) {
            instant = day.atTime(12, 0).toInstant(ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
