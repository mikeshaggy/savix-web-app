package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.analytics.forecast.ForecastService;
import com.mikeshaggy.backend.analytics.forecast.ForecastShadowObserver;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService;
import com.mikeshaggy.backend.analytics.forecast.OneOffDetector;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.analytics.insight.InsightResponseDto;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.budget.repository.CategoryBudgetRepository;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.dashboard.dto.DashboardSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedProgressDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.RiskIndicatorDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.transfer.service.TransferSummaryService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 6 de-dup at the service level: the real chain DashboardSummaryService -> SpendingProjectionService ->
 * ForecastService plus the real DashboardV2Sections, with only the data sources mocked. The historical baseline must
 * be computed exactly once per dashboard request when dashboard-v2 and forecast-v2 are both on, and the
 * {@code cycleSoFar.typicalVariableByToday} figure must be the median of the baseline cycles' cumulative variable
 * spend (computed here by hand from the fixture, not from the implementation).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardSummaryServiceBaselineOnceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Integer WALLET_ID = 1;
    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final LocalDate END = LocalDate.of(2026, 10, 8);

    // baseline cycles of 20, 20 and 21 days; a spike of 100.00 on day 3 / 5 / 8, 1.00 on every other day
    private static final PayCycle C1 = PayCycle.closed(USER_ID, WALLET_ID, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 8));
    private static final PayCycle C2 = PayCycle.closed(USER_ID, WALLET_ID, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 19));
    private static final PayCycle C3 = PayCycle.closed(USER_ID, WALLET_ID, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 30));

    @Mock private PeriodService periodService;
    @Mock private WalletService walletService;
    @Mock private AnalyticsTransactionQueryService transactionQueryService;
    @Mock private ForecastShadowObserver forecastShadowObserver;
    @Mock private FixedPaymentDashboardService fixedPaymentDashboardService;
    @Mock private InsightEngine insightEngine;
    @Mock private CategoryAggregationService categoryAggregationService;
    @Mock private CategoryBudgetRepository categoryBudgetRepository;
    @Mock private PayCycleService payCycleService;
    @Mock private TransferSummaryService transferSummaryService;
    @Mock private WalletRepository walletRepository;

    private HistoricalVariableSpendService historicalSpy;

    @BeforeEach
    void setUp() {
        historicalSpy = spy(new HistoricalVariableSpendService(payCycleService, transactionQueryService));
        when(payCycleService.history(USER_ID, HistoricalVariableSpendService.DEFAULT_CYCLES))
                .thenReturn(List.of(C1, C2, C3));
        stubCycle(C1, 3);
        stubCycle(C2, 5);
        stubCycle(C3, 8);

        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID)).thenReturn(
                Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("5000.00")).build());
        when(transactionQueryService.sum(any(), any(), any(), any(), any(CategoryType.class))).thenReturn(new BigDecimal("100.00"));
        when(transactionQueryService.sumUnlinked(any(), any(), any(), any(), any(CategoryType.class))).thenReturn(new BigDecimal("60.00"));
        when(transactionQueryService.totals(any(), any(), any(), any()))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(new BigDecimal("1000.00"), new BigDecimal("100.00")));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(any(), any(Wallet.class), any(), any()))
                .thenReturn(emptyTile());
        when(categoryAggregationService.aggregateExpenses(any(), any(), any(), any(), any(CategoryAggregationMode.class)))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), any(), any(), any(), any(), any()))
                .thenReturn(new InsightResponseDto(START, END, List.of()));
        when(categoryBudgetRepository.findActiveByWalletIdAndUserId(any(), any())).thenReturn(List.of());
        when(walletRepository.findByUserIdAndIsFundFalse(USER_ID)).thenReturn(List.of());
    }

    @Test
    void bothFlagsOn_baselineIsComputedExactlyOncePerRequestAndTypicalIsTheHandComputedMedian() {
        // day 6: cumulative variable spend by day 6 is 105 (spike day 3), 105 (spike day 5) and 6 (spike day 8) -> median 105.00
        DashboardSummaryDto result = summaryOn(LocalDate.of(2026, 9, 14), open(), flags(true, true));

        assertThat(result.cycleSoFar()).isNotNull();
        assertThat(result.cycleSoFar().typicalVariableByToday()).isEqualByComparingTo("105.00");
        assertThat(result.cycleSoFar().variableSpent()).isEqualByComparingTo("72.00"); // 6 days x 12.00
        assertThat(result.cycleSoFar().deltaVsTypical()).isEqualByComparingTo("-33.00");
        verifyBaselineCalls(1);
    }

    @Test
    void bothFlagsOn_onlyOneBaselineCycleReachesTheDay() {
        // day 21: only the 21-day cycle C3 reaches it: 20 x 1.00 + 100.00
        DashboardSummaryDto result = summaryOn(START.plusDays(20), open(), flags(true, true));

        assertThat(result.cycleSoFar().typicalVariableByToday()).isEqualByComparingTo("120.00");
        verifyBaselineCalls(1);
    }

    @Test
    void bothFlagsOn_dayBeyondAllBaselineCyclesIsNullAndStillOneBaselineCall() {
        DashboardSummaryDto result = summaryOn(START.plusDays(24), open(), flags(true, true)); // day 25

        assertThat(result.cycleSoFar()).isNotNull();
        assertThat(result.cycleSoFar().typicalVariableByToday()).isNull();
        assertThat(result.cycleSoFar().deltaVsTypical()).isNull();
        verifyBaselineCalls(1);
    }

    @Test
    void awaitingSalary_noTrajectoryExistsSoTheDashboardComputesTheBaselineItselfOnce() {
        PeriodDto awaiting = new PeriodDto(START, END, END.plusDays(1), PeriodType.PAY_CYCLE,
                CycleState.AWAITING_SALARY, END.plusDays(1), true);

        DashboardSummaryDto result = summaryOn(END.plusDays(4), awaiting, flags(true, true));

        assertThat(result.cycleSoFar()).isNotNull();
        assertThat(result.cycleSoFar().typicalVariableByToday()).isNull(); // day 34 is beyond every baseline cycle
        verifyBaselineCalls(1);
    }

    @Test
    void forecastV2OnDashboardV2Off_forecastComputesTheBaselineOnceAndNoV2SectionsAppear() {
        DashboardSummaryDto result = summaryOn(LocalDate.of(2026, 9, 14), open(), flags(true, false));

        assertThat(result.cycleSoFar()).isNull();
        assertThat(result.savings()).isNull();
        assertThat(result.categoryPressure()).isNotNull();
        verifyBaselineCalls(1);
    }

    @Test
    void unsupportedCombination_dashboardV2WithoutForecastV2_isLegacyAndNeverComputesAnyBaseline() {
        DashboardSummaryDto result = summaryOn(LocalDate.of(2026, 9, 14), open(), flags(false, true));

        assertThat(result.cycleSoFar()).isNull();
        assertThat(result.savings()).isNull();
        assertThat(result.categoryPressure()).isNotNull();
        assertThat(result.previousCyclePreview()).isNotNull();
        assertThat(result.kpis().expenses().deltaAmount()).isNotNull();
        verifyBaselineCalls(0);
    }

    // ---------------------------------------------------------------------------------------------------------

    private void verifyBaselineCalls(int expected) {
        if (expected == 0) {
            verify(historicalSpy, never()).baseline(any(), anyInt(), any(), anyInt(), anyInt());
        } else {
            verify(historicalSpy, times(expected)).baseline(any(), anyInt(), any(), anyInt(), anyInt());
        }
    }

    private static FeatureFlags flags(boolean forecastV2, boolean dashboardV2) {
        return new FeatureFlags(true, forecastV2, false, dashboardV2, false);
    }

    private DashboardSummaryDto summaryOn(LocalDate today, PeriodDto current, FeatureFlags flags) {
        Clock clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(36000), ZoneOffset.UTC);
        PeriodDto previous = new PeriodDto(C1.start(), C1.end(), START, PeriodType.LAST_PAY_CYCLE, CycleState.CLOSED, null, true);
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(current, previous));
        // the current cycle's pace-eligible series: 12.00 on every day up to the cutoff
        when(transactionQueryService.dailyVariableTotals(any(), any(), any(), any())).thenAnswer(invocation -> {
            LocalDate from = invocation.getArgument(2);
            LocalDate to = invocation.getArgument(3);
            if (from.equals(START)) {
                List<DailyTotal> series = new ArrayList<>();
                for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                    series.add(new DailyTotal(day, new BigDecimal("12.00")));
                }
                return series;
            }
            return cycleSeries(from, to);
        });

        ForecastService forecastService = new ForecastService(transactionQueryService, fixedPaymentDashboardService,
                historicalSpy, new ForecastV2Calculator(), new OneOffDetector(new ForecastV2Calculator()), clock);
        SpendingProjectionService projectionService = new SpendingProjectionService(transactionQueryService,
                walletService, periodService, fixedPaymentDashboardService, new SpendingProjectionCalculator(),
                forecastService, forecastShadowObserver, flags, clock);
        DashboardV2Sections sections = new DashboardV2Sections(transactionQueryService, historicalSpy,
                transferSummaryService, walletRepository);
        DashboardSummaryService service = new DashboardSummaryService(periodService, walletService,
                transactionQueryService, projectionService, forecastShadowObserver, fixedPaymentDashboardService,
                insightEngine, categoryAggregationService, categoryBudgetRepository, sections, flags, clock);

        return service.getSummary(WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);
    }

    private final java.util.Map<LocalDate, List<DailyTotal>> baselineSeries = new java.util.HashMap<>();

    private List<DailyTotal> cycleSeries(LocalDate from, LocalDate to) {
        return baselineSeries.getOrDefault(from, List.of());
    }

    private void stubCycle(PayCycle cycle, int spikeDay) {
        List<DailyTotal> series = new ArrayList<>();
        int index = 1;
        for (LocalDate day = cycle.start(); !day.isAfter(cycle.end()); day = day.plusDays(1), index++) {
            series.add(new DailyTotal(day, new BigDecimal(index == spikeDay ? "100.00" : "1.00")));
        }
        baselineSeries.put(cycle.start(), series);
    }

    private static PeriodDto open() {
        return new PeriodDto(START, END, END.plusDays(1), PeriodType.PAY_CYCLE, CycleState.OPEN, END.plusDays(1), true);
    }

    private static FixedTransactionsTileDto emptyTile() {
        FixedSummaryDto summary = new FixedSummaryDto(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        FixedProgressDto progress = new FixedProgressDto(0, 0, BigDecimal.ZERO, null, null, null, null, 0);
        return new FixedTransactionsTileDto(START, END, END.plusDays(1), END.plusDays(1), CycleState.OPEN, summary,
                progress, new BigDecimal("5000.00"), new BigDecimal("5000.00"), new RiskIndicatorDto(false, null),
                List.of(), List.of(), List.of(), null, List.of());
    }
}
