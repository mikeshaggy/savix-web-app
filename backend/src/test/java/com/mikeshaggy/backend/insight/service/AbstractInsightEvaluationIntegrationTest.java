package com.mikeshaggy.backend.insight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.insight.detector.InsightCandidate;
import com.mikeshaggy.backend.insight.detector.InsightContext;
import com.mikeshaggy.backend.insight.detector.InsightDetector;
import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.insight.dto.InsightListResponse;
import com.mikeshaggy.backend.insight.dto.InsightResponse;
import com.mikeshaggy.backend.insight.repository.InsightRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.repository.UserRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Stage 7.2 end to end through the real Spring proxies, transaction manager, {@link InsightWriter} and database
 * (H2 in {@code InsightEvaluationH2Test}, PostgreSQL in {@code InsightEvaluationPostgresTest}): repeated and
 * concurrent evaluation, a real unique-violation race, a read-only caller transaction, dismissal, isolation.
 * Pay cycle and period are stubbed; the context inputs are never loaded by the test detectors.
 */
@ExtendWith(OutputCaptureExtension.class)
abstract class AbstractInsightEvaluationIntegrationTest {

    static final LocalDate START = LocalDate.of(2026, 10, 9);
    static final LocalDate NEXT_ANCHOR = LocalDate.of(2026, 11, 10);

    /** Behaviour of the registered test detector; reset before every test. */
    static final AtomicReference<Function<InsightContext, Optional<InsightCandidate>>> RULE = new AtomicReference<>();
    static final AtomicReference<CyclicBarrier> BARRIER = new AtomicReference<>();

    @TestConfiguration
    static class Detectors {
        @Bean
        InsightDetector configurableTestDetector() {
            return new InsightDetector() {
                @Override
                public InsightType type() {
                    return InsightType.CATEGORY_ABOVE_TYPICAL;
                }

                @Override
                public Optional<InsightCandidate> detect(InsightContext ctx) {
                    CyclicBarrier barrier = BARRIER.get();
                    if (barrier != null) {
                        try {
                            barrier.await(10, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    Function<InsightContext, Optional<InsightCandidate>> rule = RULE.get();
                    return rule == null ? Optional.empty() : rule.apply(ctx);
                }
            };
        }

        @Bean
        InsightDetector failingTestDetector() {
            return new InsightDetector() {
                @Override
                public InsightType type() {
                    return InsightType.HIGH_IMPULSE_SPENDING;
                }

                @Override
                public Optional<InsightCandidate> detect(InsightContext ctx) {
                    throw new IllegalStateException("detector failure must not reach the caller");
                }
            };
        }
    }

    @Autowired
    InsightService insightService;

    @Autowired
    InsightRepository insightRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    WalletRepository walletRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    PayCycleService payCycleService;

    @MockitoBean
    PeriodService periodService;

    @MockitoSpyBean
    InsightWriter insightWriter;

    User user;
    Wallet wallet;
    User otherUser;
    Wallet otherWallet;

    @BeforeEach
    void setUpData() {
        RULE.set(null);
        BARRIER.set(null);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder().email("insights-" + suffix + "@example.com")
                .username("insights-" + suffix).passwordHash("hash").build());
        wallet = walletRepository.save(Wallet.builder().user(user).name("Daily").balance(new BigDecimal("100.00")).build());
        otherUser = userRepository.save(User.builder().email("other-" + suffix + "@example.com")
                .username("other-" + suffix).passwordHash("hash").build());
        otherWallet = walletRepository.save(Wallet.builder().user(otherUser).name("Daily").balance(BigDecimal.ZERO).build());
        stubCycle(user.getId(), wallet.getId(), START);
        stubCycle(otherUser.getId(), otherWallet.getId(), START);
    }

    @AfterEach
    void cleanUp() {
        RULE.set(null);
        BARRIER.set(null);
        insightRepository.deleteAll(insightRepository.findAll().stream()
                .filter(i -> Set.of(user.getId(), otherUser.getId()).contains(i.getUserId()))
                .toList());
        walletRepository.deleteAll(List.of(wallet, otherWallet));
        userRepository.deleteAll(List.of(user, otherUser));
    }

    @Test
    void evaluate_persistsOnce_isIdempotent_andFailingDetectorIsIsolated() {
        RULE.set(ctx -> Optional.of(candidate("category:3")));

        InsightListResponse first = insightService.evaluate(wallet.getId(), user.getId());
        InsightListResponse second = insightService.evaluate(wallet.getId(), user.getId());

        assertThat(first.insights()).hasSize(1);
        assertThat(second.insights()).isEqualTo(first.insights());
        assertThat(rows()).hasSize(1);
    }

    @Test
    void emptyRegistryBehaviour_noCandidate_writesNothing() {
        InsightListResponse response = insightService.evaluate(wallet.getId(), user.getId());

        assertThat(response.insights()).isEmpty();
        assertThat(rows()).isEmpty();
    }

    @Test
    void payload_roundTripsThroughTheDatabase_withExactDecimals() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", new BigDecimal("314.84"));
        payload.put("typical", new BigDecimal("162.30"));
        payload.put("since", LocalDate.of(2026, 10, 9));
        RULE.set(ctx -> Optional.of(new InsightCandidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", "Groceries above typical", "314.84 vs 162.30", payload, InsightActionType.LINK,
                "/analytics/breakdown?category=3")));

        InsightResponse insight = insightService.evaluate(wallet.getId(), user.getId()).insights().getFirst();

        assertThat(insight.payload().get("current").decimalValue()).isEqualTo(new BigDecimal("314.84"));
        assertThat(insight.payload().get("typical").toString()).isEqualTo("162.30");
        assertThat(insight.payload().get("since").asText()).isEqualTo("2026-10-09");
        assertThat(insight.actionRef()).isEqualTo("/analytics/breakdown?category=3");
    }

    @Test
    void concurrentEvaluations_storeExactlyOneRow_andAllSucceed() throws Exception {
        int threads = 6;
        BARRIER.set(new CyclicBarrier(threads));
        RULE.set(ctx -> Optional.of(candidate("category:3")));

        List<InsightListResponse> responses = runConcurrently(threads,
                () -> insightService.evaluate(wallet.getId(), user.getId()));

        assertThat(rows()).hasSize(1);
        Long id = rows().getFirst().getId();
        assertThat(responses).allSatisfy(response -> assertThat(response.insights())
                .singleElement().satisfies(insight -> assertThat(insight.id()).isEqualTo(id)));
    }

    @Test
    void lostRace_realUniqueViolation_isAbsorbed_andTheRowFromTheWinnerIsReturned() {
        RULE.set(ctx -> Optional.of(candidate("category:3")));
        AtomicBoolean raced = new AtomicBoolean();
        doAnswer(invocation -> {
            Insight ours = invocation.getArgument(0);
            if (raced.compareAndSet(false, true)) {
                insertCompetingRowInAnotherTransaction(ours);
            }
            return invocation.callRealMethod();
        }).when(insightWriter).insert(any());

        InsightListResponse response = insightService.evaluate(wallet.getId(), user.getId());

        assertThat(raced).isTrue();
        assertThat(rows()).singleElement().satisfies(row -> assertThat(row.getTitle()).isEqualTo("winner"));
        assertThat(response.insights()).singleElement()
                .satisfies(insight -> assertThat(insight.title()).isEqualTo("winner"));
    }

    @Test
    void readOnlyCallerTransaction_isNeitherWrittenThroughNorPoisoned_evenOnALostRace() {
        RULE.set(ctx -> Optional.of(candidate("category:3")));
        AtomicBoolean raced = new AtomicBoolean();
        doAnswer(invocation -> {
            if (raced.compareAndSet(false, true)) {
                insertCompetingRowInAnotherTransaction(invocation.getArgument(0));
            }
            return invocation.callRealMethod();
        }).when(insightWriter).insert(any());
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        InsightListResponse response = readOnly.execute(status ->
                insightService.evaluate(wallet.getId(), user.getId()));

        assertThat(response.insights()).hasSize(1);
        assertThat(rows()).hasSize(1);
    }

    @Test
    void callInsideACallerTransaction_isLoggedAsABoundaryViolation_callOutsideIsNot(CapturedOutput output) {
        insightService.evaluate(wallet.getId(), user.getId());
        assertThat(output.getAll()).doesNotContain("insight.evaluate.inside-transaction");

        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        readOnly.execute(status -> insightService.evaluate(wallet.getId(), user.getId()));

        assertThat(output.getAll()).containsOnlyOnce("insight.evaluate.inside-transaction walletId=" + wallet.getId());
    }

    @Test
    void readOnlyCallerTransaction_newInsightIsCommitted() {
        RULE.set(ctx -> Optional.of(candidate("category:3")));
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        InsightListResponse response = readOnly.execute(status ->
                insightService.evaluate(wallet.getId(), user.getId()));

        assertThat(response.insights()).hasSize(1);
        assertThat(rows()).hasSize(1);
    }

    @Test
    void dismissed_staysDismissed_acrossReevaluation_andDismissIsIdempotent() {
        RULE.set(ctx -> Optional.of(candidate("category:3")));
        Long id = insightService.evaluate(wallet.getId(), user.getId()).insights().getFirst().id();

        insightService.dismiss(id, user.getId());
        insightService.dismiss(id, user.getId());
        InsightListResponse after = insightService.evaluate(wallet.getId(), user.getId());

        assertThat(after.insights()).isEmpty();
        assertThat(rows()).singleElement().satisfies(row -> assertThat(row.getDismissedAt()).isNotNull());
    }

    @Test
    void usersAndWallets_areIsolated() {
        RULE.set(ctx -> Optional.of(candidate("category:3")));
        Long mine = insightService.evaluate(wallet.getId(), user.getId()).insights().getFirst().id();
        Long theirs = insightService.evaluate(otherWallet.getId(), otherUser.getId()).insights().getFirst().id();

        assertThat(mine).isNotEqualTo(theirs);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> insightService.dismiss(theirs, user.getId()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> insightService.evaluate(otherWallet.getId(), user.getId()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        assertThat(insightService.evaluate(otherWallet.getId(), otherUser.getId()).insights())
                .extracting(InsightResponse::id).containsExactly(theirs);
    }

    @Test
    void newCycle_startsAFreshSet() {
        RULE.set(ctx -> Optional.of(candidate("category:3")));
        insightService.evaluate(wallet.getId(), user.getId());

        stubCycle(user.getId(), wallet.getId(), NEXT_ANCHOR);
        InsightListResponse next = insightService.evaluate(wallet.getId(), user.getId());

        assertThat(next.cycleStart()).isEqualTo(NEXT_ANCHOR);
        assertThat(next.insights()).singleElement()
                .satisfies(insight -> assertThat(insight.cycleStart()).isEqualTo(NEXT_ANCHOR));
        assertThat(rows()).extracting(Insight::getCycleStart).containsExactlyInAnyOrder(START, NEXT_ANCHOR);
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    List<Insight> rows() {
        return insightRepository.findAll().stream()
                .filter(i -> i.getUserId().equals(user.getId()))
                .collect(Collectors.toList());
    }

    private void insertCompetingRowInAnotherTransaction(Insight ours) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> insightRepository.saveAndFlush(Insight.builder()
                .userId(ours.getUserId()).walletId(ours.getWalletId()).type(ours.getType())
                .severity(ours.getSeverity()).cycleStart(ours.getCycleStart()).dedupeKey(ours.getDedupeKey())
                .title("winner").build()));
    }

    private void stubCycle(UUID userId, Integer walletId, LocalDate start) {
        PayCycle cycle = PayCycle.open(userId, walletId, start, start.plusMonths(1));
        when(payCycleService.current(userId)).thenReturn(Optional.of(cycle));
        when(periodService.resolve(eq(PeriodType.PAY_CYCLE), eq(walletId), eq(userId), any(), any()))
                .thenReturn(new PeriodDto(cycle.start(), cycle.end(), cycle.expectedNextAnchor(), PeriodType.PAY_CYCLE,
                        CycleState.OPEN, cycle.expectedNextAnchor(), true));
    }

    private static InsightCandidate candidate(String key) {
        return new InsightCandidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, key,
                "Groceries above typical", null, null, InsightActionType.LINK, "/analytics/breakdown?category=3");
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
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
