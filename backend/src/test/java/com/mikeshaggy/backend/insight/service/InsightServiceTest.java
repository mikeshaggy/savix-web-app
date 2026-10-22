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
import com.mikeshaggy.backend.insight.detector.MultiInsightDetector;
import com.mikeshaggy.backend.insight.domain.InsightType.Recurrence;
import com.mikeshaggy.backend.insight.domain.InsightKind;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AtomicInteger refreshes = new AtomicInteger();

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

    // --- Stage 7.3 lifecycle: recurrence and stale-insight reconciliation ------------------------------------------

    @Test
    void lifecyclePolicy_isPinnedPerType() {
        assertThat(Arrays.stream(InsightType.values()).filter(InsightType::conditional)).containsExactlyInAnyOrder(
                InsightType.CATEGORY_ABOVE_TYPICAL, InsightType.SAVINGS_FUNDING_DAILY,
                InsightType.ONE_OFF_SKEWING_FORECAST, InsightType.FIXED_PAYMENT_NOT_COVERABLE,
                InsightType.FIXED_PAYMENT_AMOUNT_DRIFT, InsightType.RECURRING_MERCHANT_DETECTED,
                InsightType.HIGH_IMPULSE_SPENDING);
        assertThat(Arrays.stream(InsightType.values()).filter(t -> t.recurrence() == Recurrence.ONCE_PER_WALLET))
                .containsExactlyInAnyOrder(InsightType.CYCLE_CLOSED, InsightType.PRE_PAYDAY_NEGATIVE);
        assertThat(Arrays.stream(InsightType.values()).filter(t -> t.recurrence() == Recurrence.UNTIL_DISMISSED))
                .containsExactly(InsightType.RECURRING_MERCHANT_DETECTED);
    }

    @Test
    void cycleClosed_isStoredOncePerClosedCycle_acrossRollover() {
        AtomicReference<String> key = new AtomicReference<>("cycle:2026-09-09");
        InsightService service = service(detector(InsightType.CYCLE_CLOSED,
                ctx -> Optional.of(candidate(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, key.get()))));

        assertThat(service.evaluate(WALLET_ID, USER).insights()).hasSize(1);
        assertThat(service.evaluate(WALLET_ID, USER).insights()).hasSize(1);

        // the next salary arrived: a new cycle, and the cycle that closed is the one before it
        LocalDate next = LocalDate.of(2026, 11, 10);
        stubCycle(PayCycle.open(USER, WALLET_ID, next, LocalDate.of(2026, 12, 10)));
        key.set("cycle:2026-10-09");
        InsightListResponse november = service.evaluate(WALLET_ID, USER);
        service.evaluate(WALLET_ID, USER);

        assertThat(november.insights()).singleElement()
                .satisfies(insight -> assertThat(insight.cycleStart()).isEqualTo(next));
        assertThat(table).extracting(Insight::getDedupeKey).containsExactly("cycle:2026-09-09", "cycle:2026-10-09");
    }

    @Test
    void oncePerWallet_anOldClosedCycleIsNeverReEmitted_evenUnderAnotherCurrentCycle() {
        InsightService service = service(fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle:2026-09-09"));
        service.evaluate(WALLET_ID, USER);

        // an anchor correction moves the current cycle start, but the newest closed cycle is still Sep 9 – Oct 8
        stubCycle(PayCycle.open(USER, WALLET_ID, START.plusDays(1), NEXT_ANCHOR));
        InsightListResponse response = service.evaluate(WALLET_ID, USER);

        assertThat(response.insights()).isEmpty();
        assertThat(table).hasSize(1);
        assertThat(inserts).hasValue(1);
    }

    @Test
    void oncePerCycle_conditionRecursInTheNextCycle() {
        InsightService service = service(fires(InsightType.SAVINGS_FUNDING_DAILY, InsightSeverity.WARN, "savings:2"));
        service.evaluate(WALLET_ID, USER);
        service.dismiss(table.getFirst().getId(), USER);

        stubCycle(PayCycle.open(USER, WALLET_ID, NEXT_ANCHOR, LocalDate.of(2026, 12, 10)));
        InsightListResponse next = service.evaluate(WALLET_ID, USER);

        assertThat(next.insights()).hasSize(1);
        assertThat(table).hasSize(2);
    }

    @Test
    void untilDismissed_aDismissedMerchantIsNotSuggestedAgain_inLaterCycles() {
        AtomicReference<List<String>> keys = new AtomicReference<>(List.of("merchant:fx stream"));
        InsightService service = service(multi(InsightType.RECURRING_MERCHANT_DETECTED, keys));
        Long id = service.evaluate(WALLET_ID, USER).insights().getFirst().id();
        service.dismiss(id, USER);

        stubCycle(PayCycle.open(USER, WALLET_ID, NEXT_ANCHOR, LocalDate.of(2026, 12, 10)));
        keys.set(List.of("merchant:fx stream", "merchant:fx gym"));
        InsightListResponse next = service.evaluate(WALLET_ID, USER);

        assertThat(next.insights()).extracting(InsightResponse::title).hasSize(1);
        assertThat(table).extracting(Insight::getDedupeKey).containsExactly("merchant:fx stream", "merchant:fx gym");
    }

    @Test
    void untilDismissed_aMerchantThatWasNotDismissed_recursNextCycle() {
        InsightService service = service(fires(InsightType.RECURRING_MERCHANT_DETECTED, InsightSeverity.INFO,
                "merchant:fx stream"));
        service.evaluate(WALLET_ID, USER);

        stubCycle(PayCycle.open(USER, WALLET_ID, NEXT_ANCHOR, LocalDate.of(2026, 12, 10)));
        assertThat(service.evaluate(WALLET_ID, USER).insights()).hasSize(1);
        assertThat(table).hasSize(2);
    }

    @Test
    void conditional_noLongerDetected_isHidden_notDeletedNorDismissed_andReturnsIfDetectedAgain() {
        AtomicReference<List<String>> keys = new AtomicReference<>(List.of("transaction:300"));
        InsightService service = service(multi(InsightType.ONE_OFF_SKEWING_FORECAST, keys));
        assertThat(service.evaluate(WALLET_ID, USER).insights()).hasSize(1);

        // the one-off was excluded: the forecast no longer reports it
        keys.set(List.of());
        assertThat(service.evaluate(WALLET_ID, USER).insights()).isEmpty();
        assertThat(table).singleElement().satisfies(row -> assertThat(row.getDismissedAt()).isNull());

        // excluded again by mistake and re-included: the same row is listed, nothing new is stored
        keys.set(List.of("transaction:300"));
        assertThat(service.evaluate(WALLET_ID, USER).insights()).hasSize(1);
        assertThat(inserts).hasValue(1);
    }

    @Test
    void conditional_whoseDetectorFails_isHidden_neverShownStale() {
        AtomicBoolean failing = new AtomicBoolean();
        InsightService service = service(detector(InsightType.FIXED_PAYMENT_NOT_COVERABLE, ctx -> {
            if (failing.get()) {
                throw new IllegalStateException("forecast failed");
            }
            return Optional.of(candidate(InsightType.FIXED_PAYMENT_NOT_COVERABLE, InsightSeverity.ALERT, "occurrence:5"));
        }));
        service.evaluate(WALLET_ID, USER);
        failing.set(true);

        assertThat(service.evaluate(WALLET_ID, USER).insights()).isEmpty();
        assertThat(table).hasSize(1);
    }

    @Test
    void event_staysListedForItsCycle_whenNoLongerDetected() {
        AtomicBoolean firing = new AtomicBoolean(true);
        InsightService service = service(detector(InsightType.PRE_PAYDAY_NEGATIVE, ctx -> firing.get()
                ? Optional.of(candidate(InsightType.PRE_PAYDAY_NEGATIVE, InsightSeverity.WARN, "cycle:2026-09-09"))
                : Optional.empty()));
        service.evaluate(WALLET_ID, USER);
        firing.set(false);

        assertThat(service.evaluate(WALLET_ID, USER).insights()).hasSize(1);
    }

    @Test
    void conditional_dismissed_staysDismissed_whenDetectedAgain() {
        InsightService service = service(fires(InsightType.HIGH_IMPULSE_SPENDING, InsightSeverity.WARN, "impulse"));
        service.dismiss(service.evaluate(WALLET_ID, USER).insights().getFirst().id(), USER);

        assertThat(service.evaluate(WALLET_ID, USER).insights()).isEmpty();
        assertThat(table).hasSize(1);
        assertThat(inserts).hasValue(1);
    }

    @Test
    void multiCandidateDetector_storesEachEvent_andHidesOnlyTheResolvedOne() {
        AtomicReference<List<String>> keys = new AtomicReference<>(List.of("category:3", "category:4"));
        InsightService service = service(multi(InsightType.CATEGORY_ABOVE_TYPICAL, keys));
        assertThat(service.evaluate(WALLET_ID, USER).insights()).hasSize(2);

        keys.set(List.of("category:4"));
        assertThat(service.evaluate(WALLET_ID, USER).insights()).extracting(InsightResponse::title)
                .containsExactly("category:4");
        assertThat(table).hasSize(2);
    }

    @Test
    void multiCandidateDetector_duplicateKeysInOneEvaluation_areStoredOnce() {
        InsightService service = service(multi(InsightType.CATEGORY_ABOVE_TYPICAL,
                new AtomicReference<>(List.of("category:3", "category:3"))));

        assertThat(service.evaluate(WALLET_ID, USER).insights()).hasSize(1);
        assertThat(inserts).hasValue(1);
    }

    private static InsightDetector multi(InsightType type, AtomicReference<List<String>> keys) {
        return new MultiInsightDetector() {
            @Override
            public InsightType type() {
                return type;
            }

            @Override
            public List<InsightCandidate> detectAll(InsightContext ctx) {
                return keys.get().stream()
                        .map(key -> new InsightCandidate(type, InsightSeverity.WARN, key, key, null, null, null, null))
                        .toList();
            }
        };
    }

    // --- Stage 7.3 refresh of condition insights (Session X.1) -----------------------------------------------------

    @Test
    void condition_changedValues_areRefreshedInPlace_keepingIdCreationAndKey() {
        AtomicReference<String> amount = new AtomicReference<>("350.00");
        InsightService service = service(detector(InsightType.HIGH_IMPULSE_SPENDING, ctx -> Optional.of(
                new InsightCandidate(InsightType.HIGH_IMPULSE_SPENDING, InsightSeverity.WARN, "impulse",
                        "High impulse spending", amount.get() + " PLN", Map.of("shouldntHave", new BigDecimal(amount.get())),
                        InsightActionType.LINK, "/analytics/breakdown"))));
        InsightResponse first = service.evaluate(WALLET_ID, USER).insights().getFirst();

        amount.set("900.00");
        InsightResponse refreshed = service.evaluate(WALLET_ID, USER).insights().getFirst();

        assertThat(refreshed.id()).isEqualTo(first.id());
        assertThat(refreshed.createdAt()).isEqualTo(first.createdAt());
        assertThat(refreshed.description()).isEqualTo("900.00 PLN");
        assertThat(refreshed.payload().get("shouldntHave").decimalValue()).isEqualByComparingTo("900.00");
        assertThat(table).singleElement().satisfies(row -> {
            assertThat(row.getDedupeKey()).isEqualTo("impulse");
            assertThat(row.getCycleStart()).isEqualTo(START);
            assertThat(row.getDismissedAt()).isNull();
        });
        assertThat(inserts).hasValue(1);
        assertThat(refreshes).hasValue(1);
    }

    @Test
    void condition_severityAndAction_areRefreshedToo() {
        AtomicReference<InsightSeverity> severity = new AtomicReference<>(InsightSeverity.WARN);
        InsightService service = service(detector(InsightType.FIXED_PAYMENT_NOT_COVERABLE, ctx -> Optional.of(
                new InsightCandidate(InsightType.FIXED_PAYMENT_NOT_COVERABLE, severity.get(), "occurrence:5", "t", null,
                        null, severity.get() == InsightSeverity.ALERT ? InsightActionType.LINK : null,
                        severity.get() == InsightSeverity.ALERT ? "/transactions/fixed-payments" : null))));
        service.evaluate(WALLET_ID, USER);

        severity.set(InsightSeverity.ALERT);
        InsightResponse refreshed = service.evaluate(WALLET_ID, USER).insights().getFirst();

        assertThat(refreshed.severity()).isEqualTo(InsightSeverity.ALERT);
        assertThat(refreshed.actionType()).isEqualTo(InsightActionType.LINK);
        assertThat(refreshed.actionRef()).isEqualTo("/transactions/fixed-payments");
    }

    @Test
    void condition_unchangedContent_writesNothing_evenWithAnotherPayloadKeyOrder() {
        AtomicBoolean reversed = new AtomicBoolean();
        InsightService service = service(detector(InsightType.CATEGORY_ABOVE_TYPICAL, ctx -> {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            if (reversed.get()) {
                payload.put("typical", new BigDecimal("162.34"));
                payload.put("current", new BigDecimal("314.84"));
            } else {
                payload.put("current", new BigDecimal("314.84"));
                payload.put("typical", new BigDecimal("162.34"));
            }
            return Optional.of(new InsightCandidate(InsightType.CATEGORY_ABOVE_TYPICAL, InsightSeverity.WARN,
                    "category:3", "Groceries above typical", "d", payload, InsightActionType.LINK, "/x"));
        }));
        service.evaluate(WALLET_ID, USER);
        service.evaluate(WALLET_ID, USER);
        reversed.set(true);
        service.evaluate(WALLET_ID, USER);

        assertThat(refreshes).hasValue(0);
        verify(writer, never()).refresh(any(), any());
    }

    @Test
    void condition_dismissed_isNeverRewrittenNorRecreated_whenItsValuesChange() {
        AtomicReference<String> amount = new AtomicReference<>("350.00");
        InsightService service = service(detector(InsightType.HIGH_IMPULSE_SPENDING, ctx -> Optional.of(
                new InsightCandidate(InsightType.HIGH_IMPULSE_SPENDING, InsightSeverity.WARN, "impulse", "t",
                        amount.get(), null, null, null))));
        service.dismiss(service.evaluate(WALLET_ID, USER).insights().getFirst().id(), USER);

        amount.set("900.00");
        InsightListResponse response = service.evaluate(WALLET_ID, USER);

        assertThat(response.insights()).isEmpty();
        assertThat(table).singleElement().satisfies(row -> {
            assertThat(row.getDescription()).isEqualTo("350.00");
            assertThat(row.getDismissedAt()).isNotNull();
        });
        assertThat(inserts).hasValue(1);
        assertThat(refreshes).hasValue(0);
    }

    @Test
    void condition_dismissedWhileBeingRefreshed_staysDismissed() {
        AtomicReference<String> amount = new AtomicReference<>("350.00");
        InsightService service = service(detector(InsightType.HIGH_IMPULSE_SPENDING, ctx -> Optional.of(
                new InsightCandidate(InsightType.HIGH_IMPULSE_SPENDING, InsightSeverity.WARN, "impulse", "t",
                        amount.get(), null, null, null))));
        service.evaluate(WALLET_ID, USER);
        // the row is read as active, then dismissed by another request before the refresh is written
        when(repository.findActiveByWalletTypeCycleAndKey(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Optional<Insight> active = table.stream().filter(row -> row.getDismissedAt() == null).findFirst()
                    .map(InsightServiceTest::copy);
            table.forEach(row -> row.setDismissedAt(CLOCK.instant()));
            return active;
        });

        amount.set("900.00");
        assertThat(service.evaluate(WALLET_ID, USER).insights()).isEmpty();
        assertThat(table).singleElement().satisfies(row -> {
            assertThat(row.getDescription()).isEqualTo("350.00");
            assertThat(row.getDismissedAt()).isNotNull();
        });
    }

    @Test
    void event_keepsTheSnapshotOfItsFirstDetection() {
        AtomicReference<String> variable = new AtomicReference<>("3600.60");
        InsightService service = service(detector(InsightType.CYCLE_CLOSED, ctx -> Optional.of(new InsightCandidate(
                InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle:2026-09-09", "Cycle closed", variable.get(),
                null, null, null))));
        service.evaluate(WALLET_ID, USER);

        variable.set("3700.00");
        assertThat(service.evaluate(WALLET_ID, USER).insights().getFirst().description()).isEqualTo("3600.60");
        verify(repository, never()).findActiveByWalletTypeCycleAndKey(any(), any(), any(), any(), any());
        assertThat(refreshes).hasValue(0);
    }

    @Test
    void kind_comesFromTheType_cycleClosedIsLifecycle_everythingElseNotable() {
        InsightListResponse response = service(
                fires(InsightType.CYCLE_CLOSED, InsightSeverity.INFO, "cycle:2026-09-09"),
                fires(InsightType.SALARY_NOT_RECEIVED, InsightSeverity.INFO, "expected:2026-11-10"))
                .evaluate(WALLET_ID, USER);

        assertThat(response.insights()).extracting(InsightResponse::type, InsightResponse::kind)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(InsightType.CYCLE_CLOSED, InsightKind.LIFECYCLE),
                        org.assertj.core.groups.Tuple.tuple(InsightType.SALARY_NOT_RECEIVED, InsightKind.NOTABLE));
        assertThat(Arrays.stream(InsightType.values()).filter(type -> type.kind() == InsightKind.LIFECYCLE))
                .containsExactly(InsightType.CYCLE_CLOSED);
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
        when(repository.existsByWalletTypeAndKey(any(), any(), any(), any())).thenAnswer(inv -> table.stream()
                .anyMatch(row -> row.getUserId().equals(inv.getArgument(0)) && row.getWalletId().equals(inv.getArgument(1))
                        && row.getType() == inv.getArgument(2) && row.getDedupeKey().equals(inv.getArgument(3))));
        when(repository.findActiveByWalletTypeCycleAndKey(any(), any(), any(), any(), any())).thenAnswer(inv -> table.stream()
                .filter(row -> row.getUserId().equals(inv.getArgument(0)) && row.getWalletId().equals(inv.getArgument(1))
                        && row.getType() == inv.getArgument(2) && row.getCycleStart().equals(inv.getArgument(3))
                        && row.getDedupeKey().equals(inv.getArgument(4)) && row.getDismissedAt() == null)
                .findFirst().map(InsightServiceTest::copy));
        when(writer.refresh(any(), any())).thenAnswer(inv -> {
            Insight current = inv.getArgument(0);
            InsightWriter.InsightContent content = inv.getArgument(1);
            int updated = 0;
            for (Insight row : table) {
                if (row.getId().equals(current.getId()) && row.getUserId().equals(current.getUserId())
                        && row.getDismissedAt() == null) {
                    row.setSeverity(content.severity());
                    row.setTitle(content.title());
                    row.setDescription(content.description());
                    row.setPayload(content.payload());
                    row.setActionType(content.actionType());
                    row.setActionRef(content.actionRef());
                    updated++;
                }
            }
            refreshes.incrementAndGet();
            return updated;
        });
        when(repository.existsDismissedByWalletTypeAndKey(any(), any(), any(), any())).thenAnswer(inv -> table.stream()
                .anyMatch(row -> row.getUserId().equals(inv.getArgument(0)) && row.getWalletId().equals(inv.getArgument(1))
                        && row.getType() == inv.getArgument(2) && row.getDedupeKey().equals(inv.getArgument(3))
                        && row.getDismissedAt() != null));
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
