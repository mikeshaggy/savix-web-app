package com.mikeshaggy.backend.insight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.insight.detector.InsightCandidate;
import com.mikeshaggy.backend.insight.detector.InsightContext;
import com.mikeshaggy.backend.insight.detector.InsightContext.Sources;
import com.mikeshaggy.backend.insight.detector.InsightContext.WalletSnapshot;
import com.mikeshaggy.backend.insight.detector.InsightDetector;
import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.insight.dto.InsightAvailability;
import com.mikeshaggy.backend.insight.dto.InsightListResponse;
import com.mikeshaggy.backend.insight.dto.InsightResponse;
import com.mikeshaggy.backend.insight.repository.InsightRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Stage 7.2 framework semantics against an in-memory insights store that behaves like the table (unique
 * (wallet, type, cycle start, dedupe key), dismissed rows kept). Context sources, pay cycle and period are stubbed.
 */
class InsightServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_USER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final Integer WALLET_ID = 1;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final LocalDate START = LocalDate.of(2026, 10, 9);
    private static final LocalDate NEXT_ANCHOR = LocalDate.of(2026, 11, 10);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private WalletService walletService;
    private PayCycleService payCycleService;
    private PeriodService periodService;
    private InsightContextFactory contextFactory;
    private InsightRepository repository;
    private InsightWriter writer;
    private FeatureFlags flags;
    private Wallet wallet;

    /** The simulated table. */
    private final List<Insight> table = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicInteger inserts = new AtomicInteger();

    @BeforeEach
    void setUp() {
        walletService = mock(WalletService.class);
        payCycleService = mock(PayCycleService.class);
        periodService = mock(PeriodService.class);
        contextFactory = mock(InsightContextFactory.class);
        repository = mock(InsightRepository.class);
        writer = mock(InsightWriter.class);
        flags = new FeatureFlags(true, false, false, false, true);
        wallet = Wallet.builder().id(WALLET_ID).name("Daily").balance(new BigDecimal("1234.56")).build();

        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER)).thenReturn(wallet);
        when(walletService.getWalletEntityByIdForUser(WALLET_ID, OTHER_USER))
                .thenThrow(new EntityNotFoundException("Wallet not found with id: 1"));
        stubCycle(PayCycle.open(USER, WALLET_ID, START, NEXT_ANCHOR));
        when(contextFactory.create(eq(USER), eq(wallet), any(), any(), eq(TODAY)))
                .thenAnswer(inv -> context(inv.getArgument(2), stubSources()));
        backRepositoryWithTable();
    }

    // --- registry ------------------------------------------------------------------------------------------------

    @Test
    void emptyRegistry_isValid_returnsNoInsights_andWritesNothing() {
        InsightListResponse response = service().evaluate(WALLET_ID, USER);

        assertThat(response.status()).isEqualTo(InsightAvailability.AVAILABLE);
        assertThat(response.walletId()).isEqualTo(WALLET_ID);
        assertThat(response.cycleStart()).isEqualTo(START);
        assertThat(response.cycleEnd()).isEqualTo(NEXT_ANCHOR.minusDays(1));
        assertThat(response.cycleState()).isEqualTo(CycleState.OPEN);
        assertThat(response.insights()).isEmpty();
        assertThat(inserts).hasValue(0);
    }

    @Test
    void singleDetector_firing_isPersistedOnce_andReturned() {
        InsightListResponse response = service(fires(InsightType.PRE_PAYDAY_NEGATIVE, InsightSeverity.WARN, "2026-07-10"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.insights()).singleElement().satisfies(insight -> {
            assertThat(insight.type()).isEqualTo(InsightType.PRE_PAYDAY_NEGATIVE);
            assertThat(insight.severity()).isEqualTo(InsightSeverity.WARN);
            assertThat(insight.cycleStart()).isEqualTo(START);
            assertThat(insight.title()).isEqualTo("PRE_PAYDAY_NEGATIVE title");
            assertThat(insight.actionType()).isEqualTo(InsightActionType.LINK);
            assertThat(insight.actionRef()).isEqualTo("/wallets/balance-history");
            assertThat(insight.id()).isNotNull();
        });
        assertThat(table).singleElement().satisfies(row -> {
            assertThat(row.getUserId()).isEqualTo(USER);
            assertThat(row.getWalletId()).isEqualTo(WALLET_ID);
            assertThat(row.getDedupeKey()).isEqualTo("2026-07-10");
        });
    }

    @Test
    void detectorNotFiring_writesNothing() {
        InsightListResponse response = service(silent(InsightType.CYCLE_CLOSED)).evaluate(WALLET_ID, USER);

        assertThat(response.insights()).isEmpty();
        assertThat(inserts).hasValue(0);
    }

    @Test
    void multipleDetectors_orderedBySeverityDesc_thenCreationOrder() {
        InsightListResponse response = service(
                fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "k"),
                fires(InsightType.SAVINGS_FUNDING_DAILY, InsightSeverity.WARN, "k"),
                fires(InsightType.FIXED_PAYMENT_NOT_COVERABLE, InsightSeverity.ALERT, "k"),
                fires(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "k"))
                .evaluate(WALLET_ID, USER);

        // detectors run in InsightType order: CATEGORY_ABOVE_TYPICAL (id 1), SAVINGS_FUNDING_DAILY (2),
        // FIXED_PAYMENT_NOT_COVERABLE (3), CYCLE_CLOSED (4)
        assertThat(response.insights()).extracting(InsightResponse::type).containsExactly(
                InsightType.FIXED_PAYMENT_NOT_COVERABLE,
                InsightType.CATEGORY_ABOVE_TYPICAL,
                InsightType.SAVINGS_FUNDING_DAILY,
                InsightType.CYCLE_CLOSED);
    }

    @Test
    void ordering_isIndependentOfDetectorRegistrationOrder() {
        List<InsightType> first = service(
                fires(InsightType.CYCLE_CLOSED, InsightSeverity.WARN, "k"),
                fires(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "k"))
                .evaluate(WALLET_ID, USER).insights().stream().map(InsightResponse::type).toList();
        table.clear();
        List<InsightType> second = service(
                fires(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "k"),
                fires(InsightType.CYCLE_CLOSED, InsightSeverity.WARN, "k"))
                .evaluate(WALLET_ID, USER).insights().stream().map(InsightResponse::type).toList();

        assertThat(first).containsExactly(InsightType.CATEGORY_ABOVE_TYPICAL, InsightType.CYCLE_CLOSED);
        assertThat(second).isEqualTo(first);
    }

    // --- deduplication -------------------------------------------------------------------------------------------

    @Test
    void repeatedEvaluation_isIdempotent() {
        InsightService service = service(fires(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "category:7"));

        InsightListResponse first = service.evaluate(WALLET_ID, USER);
        InsightListResponse second = service.evaluate(WALLET_ID, USER);
        InsightListResponse third = service.evaluate(WALLET_ID, USER);

        assertThat(table).hasSize(1);
        assertThat(inserts).hasValue(1);
        assertThat(second.insights()).isEqualTo(first.insights());
        assertThat(third.insights()).isEqualTo(first.insights());
    }

    @Test
    void duplicateCandidatesInOneEvaluation_storedOnce() {
        // two detector beans of the same type reporting the same event
        InsightListResponse response = service(
                fires(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "category:7"),
                fires(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.ALERT, "category:7"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.insights()).hasSize(1);
        assertThat(inserts).hasValue(1);
    }

    @Test
    void sameTypeDifferentKeys_areDistinctInsights() {
        InsightListResponse response = service(
                fires(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "category:7"),
                fires(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "category:9"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.insights()).hasSize(2);
    }

    @Test
    void dismissedInsight_isNotReturned_andNeverRecreated() {
        InsightService service = service(fires(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "category:7"));
        Long id = service.evaluate(WALLET_ID, USER).insights().getFirst().id();

        service.dismiss(id, USER);
        InsightListResponse afterDismiss = service.evaluate(WALLET_ID, USER);
        InsightListResponse again = service.evaluate(WALLET_ID, USER);

        assertThat(afterDismiss.insights()).isEmpty();
        assertThat(again.insights()).isEmpty();
        assertThat(table).singleElement().satisfies(row -> assertThat(row.getDismissedAt()).isEqualTo(CLOCK.instant()));
        assertThat(inserts).hasValue(1);
    }

    @Test
    void lostInsertRace_uniqueViolationWithRowPresent_isTreatedAsAlreadyStored() {
        // another evaluation stores the row between our pre-check and our insert
        doReturn(false)
                .doAnswer(inv -> existsInTable(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)))
                .when(repository).existsByWalletTypeCycleAndKey(any(), any(), any(), any());
        doAnswer(inv -> {
            Insight racing = copy(inv.getArgument(0));
            store(racing);
            throw uniqueViolation();
        }).when(writer).insert(any());

        InsightListResponse response = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.insights()).singleElement()
                .satisfies(insight -> assertThat(insight.type()).isEqualTo(InsightType.CYCLE_CLOSED));
        assertThat(table).hasSize(1);
    }

    @Test
    void uniqueViolationWithoutStoredRow_isNotSwallowed() {
        doThrow(uniqueViolation()).when(writer).insert(any());

        InsightService service = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"));

        assertThatThrownBy(() -> service.evaluate(WALLET_ID, USER)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void otherIntegrityViolation_isNotSwallowed() {
        doThrow(new DataIntegrityViolationException("fk",
                new SQLException("insert or update violates foreign key constraint", "23503")))
                .when(writer).insert(any());

        InsightService service = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"));

        assertThatThrownBy(() -> service.evaluate(WALLET_ID, USER)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unrelatedDatabaseError_isNotSwallowed() {
        doThrow(new DataAccessResourceFailureException("connection lost")).when(writer).insert(any());

        InsightService service = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"));

        assertThatThrownBy(() -> service.evaluate(WALLET_ID, USER))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void isUniqueViolation_readsSqlStateAnywhereInTheCauseChain() {
        assertThat(InsightService.isUniqueViolation(uniqueViolation())).isTrue();
        assertThat(InsightService.isUniqueViolation(new RuntimeException(new SQLException("x", "23503")))).isFalse();
        assertThat(InsightService.isUniqueViolation(new RuntimeException("no cause"))).isFalse();
    }

    // --- failure isolation and validation ------------------------------------------------------------------------

    @Test
    void throwingDetector_isSkipped_othersStillRun() {
        InsightListResponse response = service(
                detector(InsightType.CATEGORY_ABOVE_TYPICAL, ctx -> {
                    throw new IllegalStateException("boom");
                }),
                fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.status()).isEqualTo(InsightAvailability.AVAILABLE);
        assertThat(response.insights()).extracting(InsightResponse::type).containsExactly(InsightType.CYCLE_CLOSED);
    }

    @Test
    void failingContextInput_failsOnlyTheDetectorsThatReadIt_andIsLoadedOnce() {
        AtomicInteger loads = new AtomicInteger();
        Sources sources = new Sources(
                Optional::empty,
                () -> {
                    loads.incrementAndGet();
                    throw new IllegalStateException("comparison failed");
                },
                () -> null,
                List::of,
                List::of);
        when(contextFactory.create(eq(USER), eq(wallet), any(), any(), eq(TODAY)))
                .thenAnswer(inv -> context(inv.getArgument(2), sources));

        InsightListResponse response = service(
                detector(InsightType.CATEGORY_ABOVE_TYPICAL, ctx -> {
                    ctx.categoryBaselines();
                    return Optional.of(candidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "a"));
                }),
                detector(InsightType.HIGH_IMPULSE_SPENDING, ctx -> {
                    ctx.categoryBaselines();
                    return Optional.of(candidate(InsightType.HIGH_IMPULSE_SPENDING, InsightSeverity.WARN, "b"));
                }),
                fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.insights()).extracting(InsightResponse::type).containsExactly(InsightType.CYCLE_CLOSED);
        assertThat(loads).hasValue(1);
    }

    @Test
    void missingForecast_isEmpty_notZero_andDetectorCanDecline() {
        InsightListResponse response = service(detector(InsightType.ONE_OFF_SKEWING_FORECAST, ctx ->
                ctx.forecast().map(f -> candidate(InsightType.ONE_OFF_SKEWING_FORECAST, InsightSeverity.WARN, "x"))))
                .evaluate(WALLET_ID, USER);

        assertThat(response.insights()).isEmpty();
        assertThat(inserts).hasValue(0);
    }

    @Test
    void nullOptionalFromDetector_isIgnored() {
        InsightListResponse response = service(detector(InsightType.CYCLE_CLOSED, ctx -> null))
                .evaluate(WALLET_ID, USER);

        assertThat(response.insights()).isEmpty();
    }

    @Test
    void invalidCandidates_areRejected_validOnesKept() {
        InsightListResponse response = service(
                detector(InsightType.CATEGORY_ABOVE_TYPICAL,      // wrong type
                        ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.WARN, "k"))),
                detector(InsightType.SAVINGS_FUNDING_DAILY,       // blank key
                        ctx -> Optional.of(candidate(InsightType.SAVINGS_FUNDING_DAILY, InsightSeverity.WARN, " "))),
                detector(InsightType.PRE_PAYDAY_NEGATIVE,         // no severity
                        ctx -> Optional.of(candidate(InsightType.PRE_PAYDAY_NEGATIVE, null, "k"))),
                detector(InsightType.ONE_OFF_SKEWING_FORECAST,    // key too long
                        ctx -> Optional.of(candidate(InsightType.ONE_OFF_SKEWING_FORECAST, InsightSeverity.WARN, "k".repeat(121)))),
                detector(InsightType.FIXED_PAYMENT_AMOUNT_DRIFT,  // title missing
                        ctx -> Optional.of(new InsightCandidate(InsightType.FIXED_PAYMENT_AMOUNT_DRIFT,
                                InsightSeverity.WARN, "k", null, null, null, null, null))),
                detector(InsightType.RECURRING_MERCHANT_DETECTED, // actionRef without action type
                        ctx -> Optional.of(new InsightCandidate(InsightType.RECURRING_MERCHANT_DETECTED,
                                InsightSeverity.INFO, "k", "t", null, null, null, "/x"))),
                detector(InsightType.SALARY_NOT_RECEIVED,         // unserialisable payload
                        ctx -> Optional.of(new InsightCandidate(InsightType.SALARY_NOT_RECEIVED,
                                InsightSeverity.INFO, "k", "t", null, Map.of("bad", new Object()), null, null))),
                fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.insights()).extracting(InsightResponse::type).containsExactly(InsightType.CYCLE_CLOSED);
        assertThat(inserts).hasValue(1);
    }

    @Test
    void payload_roundTripsWithExactDecimalsAndDates() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", new BigDecimal("162.30"));
        payload.put("baseline", new BigDecimal("1234567.89"));
        payload.put("date", LocalDate.of(2026, 7, 30));
        payload.put("categoryId", 7);
        InsightListResponse response = service(detector(InsightType.CATEGORY_ABOVE_TYPICAL, ctx -> Optional.of(
                new InsightCandidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "category:7",
                        "t", "d", payload, InsightActionType.LINK, "/analytics/breakdown?category=7"))))
                .evaluate(WALLET_ID, USER);

        var json = response.insights().getFirst().payload();
        assertThat(json.get("amount").decimalValue()).isEqualTo(new BigDecimal("162.30"));
        assertThat(json.get("amount").toString()).isEqualTo("162.30");
        assertThat(json.get("baseline").decimalValue()).isEqualTo(new BigDecimal("1234567.89"));
        assertThat(json.get("date").asText()).isEqualTo("2026-07-30");
        assertThat(json.get("categoryId").intValue()).isEqualTo(7);
    }

    @Test
    void nullPayload_staysNull() {
        InsightListResponse response = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.insights().getFirst().payload()).isNull();
        assertThat(table.getFirst().getPayload()).isNull();
    }

    // --- gating --------------------------------------------------------------------------------------------------

    @Test
    void flagOff_noCycleResolution_noDetectors_noInsightTableAccess() {
        flags = new FeatureFlags(true, false, false, false, false);
        InsightDetector detector = fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle");

        InsightListResponse response = service(detector).evaluate(WALLET_ID, USER);

        assertThat(response).isEqualTo(InsightListResponse.unavailable(WALLET_ID, InsightAvailability.FEATURE_DISABLED));
        verifyNoInteractions(repository, writer, contextFactory, payCycleService, periodService);
    }

    @Test
    void foreignWallet_is404_evenWithFlagOff() {
        flags = new FeatureFlags(true, false, false, false, false);

        assertThatThrownBy(() -> service().evaluate(WALLET_ID, OTHER_USER)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(repository, writer);
    }

    @Test
    void foreignWallet_is404_withFlagOn_andNothingEvaluated() {
        assertThatThrownBy(() -> service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "c"))
                .evaluate(WALLET_ID, OTHER_USER)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(repository, writer, contextFactory);
    }

    @Test
    void noPayCycle_noHistory_returnsUnavailable_withoutWrites() {
        when(payCycleService.current(USER)).thenReturn(Optional.empty());

        InsightListResponse response = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "c"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.status()).isEqualTo(InsightAvailability.NO_PAY_CYCLE);
        assertThat(response.insights()).isEmpty();
        verifyNoInteractions(repository, writer, contextFactory);
    }

    @Test
    void nonSalaryWallet_returnsUnavailable_withoutWrites() {
        stubCycle(PayCycle.open(USER, 2, START, NEXT_ANCHOR));

        InsightListResponse response = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "c"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.status()).isEqualTo(InsightAvailability.NOT_SALARY_WALLET);
        verifyNoInteractions(repository, writer, contextFactory);
    }

    @Test
    void legacyResolvedPeriod_payCycleV2Off_returnsUnavailable_withoutWrites() {
        when(periodService.resolve(PeriodType.PAY_CYCLE, WALLET_ID, USER, null, null))
                .thenReturn(PeriodDto.of(START, NEXT_ANCHOR.minusDays(1), NEXT_ANCHOR, PeriodType.PAY_CYCLE));

        InsightListResponse response = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "c"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.status()).isEqualTo(InsightAvailability.PAY_CYCLE_V2_INACTIVE);
        verifyNoInteractions(repository, writer, contextFactory);
    }

    @Test
    void periodOfAnotherCycle_returnsUnavailable() {
        when(periodService.resolve(PeriodType.PAY_CYCLE, WALLET_ID, USER, null, null))
                .thenReturn(new PeriodDto(START.minusMonths(1), START.minusDays(1), START, PeriodType.PAY_CYCLE,
                        CycleState.OPEN, START, true));

        InsightListResponse response = service().evaluate(WALLET_ID, USER);

        assertThat(response.status()).isEqualTo(InsightAvailability.PAY_CYCLE_V2_INACTIVE);
    }

    @Test
    void awaitingSalary_isEvaluated_onTheStillCurrentCycle() {
        LocalDate expected = LocalDate.of(2026, 10, 20);
        stubCycle(PayCycle.awaitingSalary(USER, WALLET_ID, START, expected));

        InsightListResponse response = service(fires(InsightType.SALARY_NOT_RECEIVED, InsightSeverity.WARN, "salary"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.status()).isEqualTo(InsightAvailability.AVAILABLE);
        assertThat(response.cycleState()).isEqualTo(CycleState.AWAITING_SALARY);
        assertThat(response.cycleStart()).isEqualTo(START);
        assertThat(response.insights()).singleElement()
                .satisfies(insight -> assertThat(insight.cycleStart()).isEqualTo(START));
    }

    @Test
    void cycleRollover_previousCycleInsightsDropOut_andSameEventMayFireAgain() {
        InsightService service = service(fires(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN, "category:7"));
        Long oldId = service.evaluate(WALLET_ID, USER).insights().getFirst().id();
        service.dismiss(oldId, USER);

        LocalDate newStart = NEXT_ANCHOR;
        stubCycle(PayCycle.open(USER, WALLET_ID, newStart, LocalDate.of(2026, 12, 10)));
        InsightListResponse next = service.evaluate(WALLET_ID, USER);

        assertThat(next.cycleStart()).isEqualTo(newStart);
        assertThat(next.insights()).singleElement().satisfies(insight -> {
            assertThat(insight.cycleStart()).isEqualTo(newStart);
            assertThat(insight.id()).isNotEqualTo(oldId);
        });
        assertThat(table).hasSize(2);
    }

    @Test
    void cycleRollover_undismissedOldInsight_isNotShownInNewCycle() {
        InsightService service = service(detector(InsightType.CYCLE_CLOSED, ctx -> ctx.cycle().start().equals(START)
                ? Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"))
                : Optional.empty()));
        assertThat(service.evaluate(WALLET_ID, USER).insights()).hasSize(1);

        stubCycle(PayCycle.open(USER, WALLET_ID, NEXT_ANCHOR, LocalDate.of(2026, 12, 10)));

        assertThat(service.evaluate(WALLET_ID, USER).insights()).isEmpty();
    }

    // --- dismiss -------------------------------------------------------------------------------------------------

    @Test
    void dismiss_isIdempotent() {
        InsightService service = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"));
        Long id = service.evaluate(WALLET_ID, USER).insights().getFirst().id();

        service.dismiss(id, USER);
        Instant firstDismissal = table.getFirst().getDismissedAt();
        service.dismiss(id, USER);

        assertThat(table.getFirst().getDismissedAt()).isEqualTo(firstDismissal);
    }

    @Test
    void dismiss_otherUsersInsight_is404_andLeavesItActive() {
        InsightService service = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle"));
        Long id = service.evaluate(WALLET_ID, USER).insights().getFirst().id();

        assertThatThrownBy(() -> service.dismiss(id, OTHER_USER)).isInstanceOf(EntityNotFoundException.class);
        assertThat(table.getFirst().getDismissedAt()).isNull();
    }

    @Test
    void dismiss_unknownId_is404() {
        assertThatThrownBy(() -> service().dismiss(999L, USER)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void dismiss_flagOff_is404_withoutTouchingTheTable() {
        flags = new FeatureFlags(true, false, false, false, false);

        assertThatThrownBy(() -> service().dismiss(1L, USER)).isInstanceOf(EntityNotFoundException.class);
        verify(repository, never()).dismiss(anyLong(), any(), any());
        verifyNoInteractions(repository);
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private InsightService service(InsightDetector... detectors) {
        @SuppressWarnings("unchecked")
        ObjectProvider<InsightDetector> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenAnswer(inv -> Arrays.stream(detectors));
        return new InsightService(walletService, payCycleService, periodService, contextFactory, repository, writer,
                new InsightDetectorRunner(provider), flags, objectMapper, CLOCK);
    }

    private void stubCycle(PayCycle cycle) {
        when(payCycleService.current(USER)).thenReturn(Optional.of(cycle));
        when(periodService.resolve(PeriodType.PAY_CYCLE, cycle.salaryWalletId(), USER, null, null))
                .thenReturn(new PeriodDto(cycle.start(), cycle.end(), cycle.expectedNextAnchor(), PeriodType.PAY_CYCLE,
                        cycle.state(), cycle.expectedNextAnchor(), true));
    }

    private static Sources stubSources() {
        return new Sources(Optional::empty, List::of, () -> null, List::of, List::of);
    }

    private static InsightContext context(PayCycle cycle, Sources sources) {
        return new InsightContext(USER, new WalletSnapshot(WALLET_ID, "Daily", new BigDecimal("1234.56")), cycle,
                TODAY, sources);
    }

    private static InsightCandidate candidate(InsightType type, InsightSeverity severity, String key) {
        return new InsightCandidate(type, severity, key, type + " title", type + " description", null,
                InsightActionType.LINK, "/wallets/balance-history");
    }

    private static InsightDetector fires(InsightType type, InsightSeverity severity, String key) {
        return detector(type, ctx -> Optional.of(candidate(type, severity, key)));
    }

    private static InsightDetector silent(InsightType type) {
        return detector(type, ctx -> Optional.empty());
    }

    private static InsightDetector detector(InsightType type, Function<InsightContext, Optional<InsightCandidate>> rule) {
        return new InsightDetector() {
            @Override
            public InsightType type() {
                return type;
            }

            @Override
            public Optional<InsightCandidate> detect(InsightContext ctx) {
                return rule.apply(ctx);
            }
        };
    }

    private void backRepositoryWithTable() {
        when(repository.existsByWalletTypeCycleAndKey(any(), any(), any(), any()))
                .thenAnswer(inv -> existsInTable(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
        when(repository.findActiveByWalletAndCycle(any(), any(), any())).thenAnswer(inv -> table.stream()
                .filter(row -> row.getUserId().equals(inv.getArgument(0)))
                .filter(row -> row.getWalletId().equals(inv.getArgument(1)))
                .filter(row -> row.getCycleStart().equals(inv.getArgument(2)))
                .filter(row -> row.getDismissedAt() == null)
                .map(InsightServiceTest::copy)
                .toList());
        when(repository.findByIdAndUserId(anyLong(), any())).thenAnswer(inv -> table.stream()
                .filter(row -> row.getId().equals(inv.getArgument(0)) && row.getUserId().equals(inv.getArgument(1)))
                .findFirst());
        when(repository.dismiss(anyLong(), any(), any())).thenAnswer(inv -> {
            int updated = 0;
            for (Insight row : table) {
                if (row.getId().equals(inv.getArgument(0)) && row.getUserId().equals(inv.getArgument(1))
                        && row.getDismissedAt() == null) {
                    row.setDismissedAt(inv.getArgument(2));
                    updated++;
                }
            }
            return updated;
        });
        when(writer.insert(any())).thenAnswer(inv -> {
            Insight insight = inv.getArgument(0);
            if (existsInTable(insight.getWalletId(), insight.getType(), insight.getCycleStart(), insight.getDedupeKey())) {
                throw uniqueViolation();
            }
            inserts.incrementAndGet();
            return store(insight);
        });
    }

    private boolean existsInTable(Integer walletId, InsightType type, LocalDate cycleStart, String key) {
        return table.stream().anyMatch(row -> row.getWalletId().equals(walletId) && row.getType() == type
                && row.getCycleStart().equals(cycleStart) && row.getDedupeKey().equals(key));
    }

    private Insight store(Insight insight) {
        insight.setId(ids.incrementAndGet());
        insight.setCreatedAt(CLOCK.instant());
        table.add(insight);
        return insight;
    }

    private static Insight copy(Insight source) {
        return Insight.builder()
                .id(source.getId()).userId(source.getUserId()).walletId(source.getWalletId())
                .type(source.getType()).severity(source.getSeverity()).cycleStart(source.getCycleStart())
                .dedupeKey(source.getDedupeKey()).title(source.getTitle()).description(source.getDescription())
                .payload(source.getPayload()).actionType(source.getActionType()).actionRef(source.getActionRef())
                .createdAt(source.getCreatedAt()).dismissedAt(source.getDismissedAt())
                .build();
    }

    private static DataIntegrityViolationException uniqueViolation() {
        return new DataIntegrityViolationException("duplicate key",
                new SQLException("duplicate key value violates unique constraint", "23505"));
    }
}
