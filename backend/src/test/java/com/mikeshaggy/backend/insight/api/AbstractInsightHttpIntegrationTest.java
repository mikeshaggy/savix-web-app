package com.mikeshaggy.backend.insight.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.insight.detector.InsightCandidate;
import com.mikeshaggy.backend.insight.detector.InsightContext;
import com.mikeshaggy.backend.insight.detector.InsightDetector;
import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.insight.repository.InsightRepository;
import com.mikeshaggy.backend.insight.service.InsightService;
import com.mikeshaggy.backend.insight.service.InsightWriter;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stage 7.2 through the real HTTP layer: full application context, real {@code InsightService}/{@code InsightWriter}/
 * transaction manager/database and — the point of this class — Spring Boot's default open-entity-manager-in-view
 * interceptor, which keeps one EntityManager bound to the request thread across the transactions the service opens
 * and suspends. Only the current user, pay cycle and period are stubbed; the detectors are test beans.
 */
abstract class AbstractInsightHttpIntegrationTest {

    static final LocalDate START = LocalDate.of(2026, 10, 9);

    static final AtomicReference<Function<InsightContext, Optional<InsightCandidate>>> RULE_A = new AtomicReference<>();
    static final AtomicReference<Function<InsightContext, Optional<InsightCandidate>>> RULE_B = new AtomicReference<>();
    static final AtomicReference<CyclicBarrier> BARRIER = new AtomicReference<>();

    @TestConfiguration
    static class HttpDetectors {
        /** Runs first (CATEGORY_ABOVE_TYPICAL). */
        @Bean
        InsightDetector httpDetectorA() {
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
                            barrier.await(15, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    return apply(RULE_A, ctx);
                }
            };
        }

        /** Runs later (CYCLE_CLOSED). */
        @Bean
        InsightDetector httpDetectorB() {
            return new InsightDetector() {
                @Override
                public InsightType type() {
                    return InsightType.CYCLE_CLOSED;
                }

                @Override
                public Optional<InsightCandidate> detect(InsightContext ctx) {
                    return apply(RULE_B, ctx);
                }
            };
        }

        private static Optional<InsightCandidate> apply(
                AtomicReference<Function<InsightContext, Optional<InsightCandidate>>> rule, InsightContext ctx) {
            Function<InsightContext, Optional<InsightCandidate>> f = rule.get();
            return f == null ? Optional.empty() : f.apply(ctx);
        }
    }

    @Autowired
    MockMvc mockMvc;

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

    @Autowired
    org.springframework.context.ApplicationContext applicationContext;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

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
        RULE_A.set(null);
        RULE_B.set(null);
        BARRIER.set(null);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder().email("http-" + suffix + "@example.com")
                .username("http-" + suffix).passwordHash("hash").build());
        wallet = walletRepository.save(Wallet.builder().user(user).name("Daily").balance(new BigDecimal("100.00")).build());
        otherUser = userRepository.save(User.builder().email("http-other-" + suffix + "@example.com")
                .username("http-other-" + suffix).passwordHash("hash").build());
        otherWallet = walletRepository.save(Wallet.builder().user(otherUser).name("Daily").balance(BigDecimal.ZERO).build());
        stubCycle(user.getId(), wallet.getId());
        stubCycle(otherUser.getId(), otherWallet.getId());
        when(currentUserProvider.getCurrentUserId()).thenReturn(user.getId());
    }

    @AfterEach
    void cleanUp() {
        RULE_A.set(null);
        RULE_B.set(null);
        BARRIER.set(null);
        insightRepository.deleteAll(insightRepository.findAll().stream()
                .filter(i -> Set.of(user.getId(), otherUser.getId()).contains(i.getUserId()))
                .toList());
        walletRepository.deleteAll(List.of(wallet, otherWallet));
        userRepository.deleteAll(List.of(user, otherUser));
    }

    // --- GET: the request runs inside Spring's open-entity-manager-in-view ------------------------------------------

    @Test
    void openEntityManagerInView_isActiveForTheRequests() {
        assertThat(applicationContext.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isNotEmpty();
    }

    @Test
    void get_persistsAndReturnsTheContract_andIsIdempotentAcrossRequests() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("typical", new BigDecimal("162.30"));
        payload.put("current", new BigDecimal("314.84"));
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", payload)));

        MvcResult first = mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(wallet.getId()))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.cycleStart").value("2026-10-09"))
                .andExpect(jsonPath("$.cycleEnd").value("2026-11-08"))
                .andExpect(jsonPath("$.cycleState").value("OPEN"))
                .andExpect(jsonPath("$.insights.length()").value(1))
                .andExpect(jsonPath("$.insights[0].type").value("CATEGORY_ABOVE_TYPICAL"))
                .andExpect(jsonPath("$.insights[0].severity").value("WARN"))
                .andExpect(jsonPath("$.insights[0].actionType").value("LINK"))
                .andExpect(jsonPath("$.insights[0].createdAt").exists())
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();

        String body = first.getResponse().getContentAsString();
        assertThat(body).contains("\"typical\":162.30").contains("\"current\":314.84");
        // jsonb does not keep key order, so compare the documents, not the text
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(second.getResponse().getContentAsString()))
                .isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper().readTree(body));
        assertThat(rows()).hasSize(1);
    }

    @Test
    void get_lostRace_isAbsorbedInsideTheRequest_returns200WithTheWinner_andTheNextRequestStillWorks()
            throws Exception {
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", null)));
        raceOnce(InsightType.CATEGORY_ABOVE_TYPICAL);

        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.insights.length()").value(1))
                .andExpect(jsonPath("$.insights[0].title").value("winner"));

        // a later, different event on a fresh request (fresh open-in-view session) is stored normally
        RULE_B.set(ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "closed", null)));
        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.length()").value(2));
        assertThat(rows()).hasSize(2);
    }

    @Test
    void get_lostRaceOnTheFirstDetector_doesNotPreventTheNextDetectorFromStoring_inTheSameRequest() throws Exception {
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", null)));
        RULE_B.set(ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.ALERT, "closed", null)));
        raceOnce(InsightType.CATEGORY_ABOVE_TYPICAL);

        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.length()").value(2))
                .andExpect(jsonPath("$.insights[0].type").value("CYCLE_CLOSED"))
                .andExpect(jsonPath("$.insights[1].title").value("winner"));
        assertThat(rows()).hasSize(2);
    }

    @Test
    void get_lostRaceOnTheLastDetector_afterAnEarlierInsert_returns200() throws Exception {
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", null)));
        RULE_B.set(ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "closed", null)));
        raceOnce(InsightType.CYCLE_CLOSED);

        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.length()").value(2))
                .andExpect(jsonPath("$.insights[0].type").value("CATEGORY_ABOVE_TYPICAL"))
                .andExpect(jsonPath("$.insights[1].title").value("winner"));
        assertThat(rows()).hasSize(2);
    }

    @Test
    void get_orderIsSeverityDescendingThenIdAscending() throws Exception {
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.INFO,
                "a", null)));
        RULE_B.set(ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.ALERT, "b", null)));
        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk());
        // a third event with the same severity as the first, stored later
        RULE_B.set(ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.ALERT, "c", null)));

        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.length()").value(3))
                .andExpect(jsonPath("$.insights[0].severity").value("ALERT"))
                .andExpect(jsonPath("$.insights[1].severity").value("ALERT"))
                .andExpect(jsonPath("$.insights[2].severity").value("INFO"))
                .andExpect(result -> {
                    List<Integer> ids = com.jayway.jsonpath.JsonPath.read(
                            result.getResponse().getContentAsString(), "$.insights[?(@.severity=='ALERT')].id");
                    assertThat(ids).hasSize(2).isSorted();
                });
    }

    @Test
    void get_concurrentRequests_storeOneRow_andAllReturn200() throws Exception {
        int threads = 8;
        BARRIER.set(new CyclicBarrier(threads));
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", null)));
        RULE_B.set(ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "closed", null)));

        List<String> bodies = runConcurrently(threads, () -> mockMvc
                .perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.length()").value(2))
                .andReturn().getResponse().getContentAsString());

        assertThat(rows()).hasSize(2);
        assertThat(bodies).hasSize(threads).doesNotContain("");
    }

    @Test
    void get_columnLimitsAreHonoured_exactlyAtTheLimitStores_oneOverIsRejectedBeforeTheDatabase() throws Exception {
        String longText = "ą".repeat(5000);
        RULE_A.set(ctx -> Optional.of(new InsightCandidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "k".repeat(InsightCandidate.MAX_DEDUPE_KEY_LENGTH), "t".repeat(InsightCandidate.MAX_TITLE_LENGTH),
                longText, null, InsightActionType.LINK, "r".repeat(InsightCandidate.MAX_ACTION_REF_LENGTH))));
        RULE_B.set(ctx -> Optional.of(new InsightCandidate(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "ok",
                "t".repeat(InsightCandidate.MAX_TITLE_LENGTH + 1), null, null, null, null)));

        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.length()").value(1))
                .andExpect(jsonPath("$.insights[0].description").value(longText));

        RULE_B.set(ctx -> Optional.of(new InsightCandidate(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "ok",
                "t", null, null, InsightActionType.LINK, "r".repeat(InsightCandidate.MAX_ACTION_REF_LENGTH + 1))));
        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.length()").value(1));
        assertThat(rows()).hasSize(1);
    }

    @Test
    void get_aDetectorFailure_neverFailsTheRequest_andTheOthersStillStore() throws Exception {
        RULE_A.set(ctx -> {
            throw new IllegalStateException("boom");
        });
        RULE_B.set(ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "closed", null)));

        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.length()").value(1))
                .andExpect(jsonPath("$.insights[0].type").value("CYCLE_CLOSED"));
    }

    @Test
    void get_foreignWallet_is404_andStoresNothing() throws Exception {
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", null)));

        mockMvc.perform(get("/api/insights").param("walletId", otherWallet.getId().toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/insights").param("walletId", "999999"))
                .andExpect(status().isNotFound());

        assertThat(insightRepository.findAll().stream()
                .filter(i -> Set.of(user.getId(), otherUser.getId()).contains(i.getUserId()))).isEmpty();
    }

    // --- DELETE ----------------------------------------------------------------------------------------------------

    @Test
    void dismiss_removesItFromTheList_isIdempotent_andIsNotRecreated() throws Exception {
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", null)));
        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString())).andExpect(status().isOk());
        Long id = rows().getFirst().getId();

        mockMvc.perform(delete("/api/insights/" + id)).andExpect(status().isNoContent());
        var dismissedAt = insightRepository.findById(id).orElseThrow().getDismissedAt();
        mockMvc.perform(delete("/api/insights/" + id)).andExpect(status().isNoContent());

        assertThat(dismissedAt).isNotNull();
        assertThat(insightRepository.findById(id).orElseThrow().getDismissedAt()).isEqualTo(dismissedAt);
        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.insights").isEmpty());
        assertThat(rows()).hasSize(1);
    }

    @Test
    void dismiss_anotherUsersInsight_is404_andTheRowIsUnchanged() throws Exception {
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", null)));
        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString())).andExpect(status().isOk());
        Long id = rows().getFirst().getId();

        when(currentUserProvider.getCurrentUserId()).thenReturn(otherUser.getId());
        mockMvc.perform(delete("/api/insights/" + id)).andExpect(status().isNotFound());

        assertThat(insightRepository.findById(id).orElseThrow().getDismissedAt()).isNull();
        when(currentUserProvider.getCurrentUserId()).thenReturn(user.getId());
        mockMvc.perform(get("/api/insights").param("walletId", wallet.getId().toString()))
                .andExpect(jsonPath("$.insights.length()").value(1));
    }

    @Test
    void dismiss_unknownId_is404() throws Exception {
        mockMvc.perform(delete("/api/insights/987654321")).andExpect(status().isNotFound());
    }

    // --- service level, real proxies -------------------------------------------------------------------------------

    @Test
    void readOnlyCallerTransaction_isNotPoisonedByAFailingReadInsideADetector_andNewInsightsAreCommitted() {
        RULE_A.set(ctx -> {
            // a failing read-only transaction: would mark a *joined* caller transaction rollback-only
            TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
            readOnly.setReadOnly(true);
            readOnly.executeWithoutResult(status -> {
                throw new IllegalStateException("failing read");
            });
            return Optional.empty();
        });
        RULE_B.set(ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "closed", null)));
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.setReadOnly(true);

        var response = outer.execute(status -> insightService.evaluate(wallet.getId(), user.getId()));

        assertThat(response.insights()).hasSize(1);
        assertThat(rows()).hasSize(1);
    }

    @Test
    void manyConcurrentEvaluations_withSeveralEvents_storeEachEventOnce() throws Exception {
        int threads = 8;
        BARRIER.set(new CyclicBarrier(threads));
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", null)));
        RULE_B.set(ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "closed", null)));

        List<List<Long>> ids = runConcurrently(threads, () -> insightService
                .evaluate(wallet.getId(), user.getId()).insights().stream().map(i -> i.id()).toList());

        assertThat(rows()).hasSize(2);
        assertThat(ids).allSatisfy(list -> assertThat(list).hasSize(2));
        assertThat(ids.stream().distinct().count()).isEqualTo(1);
    }

    /**
     * Regression guard for the Session W tester finding (Repair 1): more simultaneous first-time evaluations than
     * Hikari connections (default 10) all complete. Detection releases its connection before any insert, so no
     * evaluation holds one connection while waiting for a second (see {@code InsightEvaluationSingleConnectionTest}).
     */
    @Test
    void concurrentFirstTimeEvaluations_beyondThePoolSize_stillComplete() throws Exception {
        int threads = 16;
        BARRIER.set(new CyclicBarrier(threads));
        RULE_A.set(ctx -> Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                "category:3", null)));

        List<Integer> sizes = runConcurrently(threads, () -> insightService
                .evaluate(wallet.getId(), user.getId()).insights().size());

        assertThat(sizes).allMatch(size -> size == 1);
        assertThat(rows()).hasSize(1);
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    List<Insight> rows() {
        return insightRepository.findAll().stream()
                .filter(i -> i.getUserId().equals(user.getId()))
                .collect(Collectors.toList());
    }

    /** The first insert of {@code type} loses a real race: a competing row is committed just before it. */
    private void raceOnce(InsightType type) {
        AtomicBoolean raced = new AtomicBoolean();
        doAnswer(invocation -> {
            Insight ours = invocation.getArgument(0);
            if (ours.getType() == type && raced.compareAndSet(false, true)) {
                TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
                requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                requiresNew.executeWithoutResult(status -> insightRepository.saveAndFlush(Insight.builder()
                        .userId(ours.getUserId()).walletId(ours.getWalletId()).type(ours.getType())
                        .severity(ours.getSeverity()).cycleStart(ours.getCycleStart())
                        .dedupeKey(ours.getDedupeKey()).title("winner").build()));
            }
            return invocation.callRealMethod();
        }).when(insightWriter).insert(any());
    }

    private void stubCycle(UUID userId, Integer walletId) {
        PayCycle cycle = PayCycle.open(userId, walletId, START, START.plusMonths(1));
        when(payCycleService.current(userId)).thenReturn(Optional.of(cycle));
        when(periodService.resolve(eq(PeriodType.PAY_CYCLE), eq(walletId), eq(userId), any(), any()))
                .thenReturn(new PeriodDto(cycle.start(), cycle.end(), cycle.expectedNextAnchor(), PeriodType.PAY_CYCLE,
                        CycleState.OPEN, cycle.expectedNextAnchor(), true));
    }

    static InsightCandidate candidate(InsightType type, InsightSeverity severity, String key,
                                      Map<String, Object> payload) {
        return new InsightCandidate(type, severity, key, "Title " + key, null, payload, InsightActionType.LINK,
                "/analytics/breakdown?category=3");
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
                results.add(future.get(40, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
