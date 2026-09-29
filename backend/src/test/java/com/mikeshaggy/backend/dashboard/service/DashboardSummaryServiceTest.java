package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregation;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator;
import com.mikeshaggy.backend.analytics.forecast.ForecastShadowObserver;
import com.mikeshaggy.backend.analytics.forecast.ForecastConfidence;
import com.mikeshaggy.backend.analytics.forecast.ForecastStatus;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.insight.PrecomputedInsightData;
import com.mikeshaggy.backend.analytics.insight.InsightDto;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.analytics.insight.InsightResponseDto;
import com.mikeshaggy.backend.analytics.insight.InsightSeverity;
import com.mikeshaggy.backend.analytics.insight.InsightType;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.dashboard.dto.DashboardCategoryDirection;
import com.mikeshaggy.backend.dashboard.dto.DashboardCycleHealthDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardHealthStatus;
import com.mikeshaggy.backend.dashboard.dto.DashboardSummaryDto;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedProgressDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.RiskIndicatorDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.budget.repository.CategoryBudgetRepository;
import com.mikeshaggy.backend.regression.September2026Fixture;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardSummaryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Integer WALLET_ID = 1;
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-26T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PeriodService periodService;

    @Mock
    private WalletService walletService;

    @Mock
    private AnalyticsTransactionQueryService transactionQueryService;

    @Mock
    private SpendingProjectionService spendingProjectionService;

    @Mock
    private ForecastShadowObserver forecastShadowObserver;

    @Mock
    private FixedPaymentDashboardService fixedPaymentDashboardService;

    @Mock
    private InsightEngine insightEngine;

    @Mock
    private com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService categoryAggregationService;

    @Mock
    private CategoryBudgetRepository categoryBudgetRepository;

    private DashboardSummaryService service;

    @BeforeEach
    void setUp() {
        service = new DashboardSummaryService(
                periodService,
                walletService,
                transactionQueryService,
                spendingProjectionService,
                forecastShadowObserver,
                fixedPaymentDashboardService,
                insightEngine,
                categoryAggregationService,
                categoryBudgetRepository,
                CLOCK);
        // default: no active budgets — keeps existing tests unaffected
        when(categoryBudgetRepository.findActiveByWalletIdAndUserId(any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void payCycleSummaryUsesSameCutoffComparisonAndMapsAllSections() {
        PeriodDto current = openCycle(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        PeriodDto compare = PeriodDto.of(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 1),
                PeriodType.LAST_PAY_CYCLE);

        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder()
                        .id(WALLET_ID)
                        .name("Main")
                        .balance(new BigDecimal("2500.00"))
                        .build());
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(current, compare));
        when(transactionQueryService.totals(WALLET_ID, USER_ID,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 26)))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("5000.00"), new BigDecimal("3100.00")));
        when(transactionQueryService.totals(WALLET_ID, USER_ID,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 26)))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("5000.00"), new BigDecimal("2860.00")));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(LocalDate.of(2026, 5, 26))))
                .thenReturn(fixedTile());
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(LocalDate.of(2026, 5, 26)),
                any()))
                .thenReturn(projection("620.00", "740.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), TODAY, List.of(
                        new InsightDto(InsightType.SAFE_TO_SPEND_WARNING, InsightSeverity.ALERT,
                                "Careful", "Safe-to-spend is low.", null, new BigDecimal("-10.00")))));
        when(categoryAggregationService.aggregateExpenses(WALLET_ID, USER_ID,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 26),
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES))
                .thenReturn(new CategoryAggregationResult(new BigDecimal("3100.00"), List.of(
                        category(1, "Groceries", "1200.00"),
                        category(2, "Transport", "800.00"))));
        when(categoryAggregationService.aggregateExpenses(WALLET_ID, USER_ID,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 26),
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES))
                .thenReturn(new CategoryAggregationResult(new BigDecimal("2860.00"), List.of(
                        category(1, "Groceries", "1000.00"),
                        category(2, "Transport", "900.00"))));

        DashboardSummaryDto result = service.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.walletId()).isEqualTo(WALLET_ID);
        assertThat(result.walletName()).isEqualTo("Main");
        assertThat(result.period().startDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(result.period().endDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(result.period().cutoffDate()).isEqualTo(LocalDate.of(2026, 5, 26));
        assertThat(result.period().compareStartDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(result.period().compareEndDate()).isEqualTo(LocalDate.of(2026, 4, 26));
        assertThat(result.period().daysInPeriod()).isEqualTo(31);
        assertThat(result.period().daysElapsed()).isEqualTo(26);
        assertThat(result.period().daysRemaining()).isEqualTo(5);
        assertThat(result.period().cycleState()).isEqualTo(CycleState.OPEN);
        assertThat(result.period().expectedPaydayDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.period().salaryWallet()).isTrue();
        assertThat(result.period().reporting()).isFalse();
        assertThat(result.cycleHealth().projectionAvailable()).isTrue();
        assertThat(result.cycleHealth().projectionReason()).isNull();
        assertThat(result.kpis().income().amount()).isEqualByComparingTo("5000.00");
        assertThat(result.kpis().expenses().deltaAmount()).isEqualByComparingTo("240.00");
        assertThat(result.kpis().expenses().deltaPercent()).isEqualByComparingTo("8.39");
        assertThat(result.kpis().saved().amount()).isEqualByComparingTo("1900.00");
        assertThat(result.kpis().savingsRate().percent()).isEqualByComparingTo("38.00");
        assertThat(result.kpis().savingsRate().deltaPercentagePoints()).isEqualByComparingTo("-4.80");
        assertThat(result.cycleHealth().status()).isEqualTo(DashboardHealthStatus.ON_TRACK);
        assertThat(result.cycleHealth().safeToSpend()).isEqualByComparingTo("620.00");
        // 620.00 / 5 daysRemaining = 124.00
        assertThat(result.cycleHealth().safeToSpendPerDay()).isEqualByComparingTo("124.00");
        assertThat(result.fixedPayments().nextPendingOccurrence().name()).isEqualTo("Rent");
        assertThat(result.fixedPayments().upcomingOccurrences()).hasSize(3);
        assertThat(result.insights()).hasSize(1);
        assertThat(result.categoryPressure()).hasSize(2);
        assertThat(result.categoryPressure().getFirst().categoryName()).isEqualTo("Groceries");
        assertThat(result.categoryPressure().getFirst().shareOfExpensesPercent()).isEqualByComparingTo("38.71");
        assertThat(result.categoryPressure().getFirst().deltaAmount()).isEqualByComparingTo("200.00");
        assertThat(result.categoryPressure().getFirst().direction()).isEqualTo(DashboardCategoryDirection.UP);
        assertThat(result.previousCyclePreview().expensesDeltaAmount()).isEqualByComparingTo("240.00");

        verify(fixedPaymentDashboardService).getFixedPaymentsTileData(
                same(current),
                any(Wallet.class),
                eq(USER_ID),
                eq(LocalDate.of(2026, 5, 26)));
    }

    @Test
    void noComparisonKeepsCurrentValuesAndNullDeltas() {
        PeriodDto current = openCycle(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("1000.00")).build());
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(current, null));
        when(transactionQueryService.totals(WALLET_ID, USER_ID, current.startDate(), TODAY))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("0.00"), new BigDecimal("100.00")));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(LocalDate.of(2026, 5, 26))))
                .thenReturn(emptyFixedTile());
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(LocalDate.of(2026, 5, 26)),
                any()))
                .thenReturn(projection("100.00", "900.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), TODAY, List.of()));
        when(categoryAggregationService.aggregateExpenses(WALLET_ID, USER_ID,
                current.startDate(), TODAY, CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES))
                .thenReturn(new CategoryAggregationResult(new BigDecimal("100.00"), List.of(
                        category(1, "Food", "100.00"))));

        DashboardSummaryDto result = service.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.period().comparisonAvailable()).isFalse();
        assertThat(result.kpis().income().amount()).isEqualByComparingTo("0.00");
        assertThat(result.kpis().income().deltaAmount()).isNull();
        assertThat(result.kpis().income().deltaPercent()).isNull();
        assertThat(result.kpis().savingsRate().percent()).isEqualByComparingTo("0.00");
        assertThat(result.kpis().savingsRate().deltaPercentagePoints()).isNull();
        assertThat(result.categoryPressure().getFirst().deltaAmount()).isNull();
        assertThat(result.categoryPressure().getFirst().direction()).isEqualTo(DashboardCategoryDirection.FLAT);
    }

    @Test
    void asOfDateBeforePeriodStartIsClampedForProjectionFixedPaymentsAndInsights() {
        PeriodDto current = payCycle();
        PeriodDto compare = previousCycle();
        LocalDate cutoff = LocalDate.of(2026, 5, 1);
        LocalDate compareCutoff = LocalDate.of(2026, 4, 1);
        stubSummaryForCutoff(current, compare, cutoff, compareCutoff);

        DashboardSummaryDto result = service.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null,
                LocalDate.of(2026, 4, 20),
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.period().asOfDate()).isEqualTo(cutoff);
        assertThat(result.period().cutoffDate()).isEqualTo(cutoff);
        verify(spendingProjectionService).getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(cutoff), any());
        verify(fixedPaymentDashboardService).getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(cutoff));
        assertInsightWindow(cutoff, compareCutoff);
    }

    @Test
    void asOfDateAfterTodayIsCappedAtTodayNotAtPeriodEnd() {
        PeriodDto current = payCycle();
        PeriodDto compare = previousCycle();
        LocalDate cutoff = TODAY;
        LocalDate compareCutoff = LocalDate.of(2026, 4, 26);
        stubSummaryForCutoff(current, compare, cutoff, compareCutoff);

        DashboardSummaryDto result = service.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null,
                LocalDate.of(2026, 6, 20),
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.period().asOfDate()).isEqualTo(cutoff);
        assertThat(result.period().cutoffDate()).isEqualTo(cutoff);
        assertThat(result.period().endDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(result.period().daysInPeriod()).isEqualTo(31);
        assertThat(result.period().daysElapsed()).isEqualTo(26);
        assertThat(result.period().daysRemaining()).isEqualTo(5);
        verify(transactionQueryService).totals(WALLET_ID, USER_ID, current.startDate(), cutoff);
        verify(spendingProjectionService).getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(cutoff), any());
        assertInsightWindow(cutoff, compareCutoff);
    }

    @Test
    void asOfDateInsidePeriodOverridesClockForDashboardDependencies() {
        PeriodDto current = payCycle();
        PeriodDto compare = previousCycle();
        LocalDate cutoff = LocalDate.of(2026, 5, 10);
        LocalDate compareCutoff = LocalDate.of(2026, 4, 10);
        stubSummaryForCutoff(current, compare, cutoff, compareCutoff);

        DashboardSummaryDto result = service.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null,
                cutoff,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.period().cutoffDate()).isEqualTo(cutoff);
        verify(transactionQueryService).totals(WALLET_ID, USER_ID, current.startDate(), cutoff);
        verify(categoryAggregationService).aggregateExpenses(
                WALLET_ID, USER_ID, current.startDate(), cutoff,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);
        verify(fixedPaymentDashboardService).getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(cutoff));
        assertInsightWindow(cutoff, compareCutoff);
    }

    @Test
    void spendingProjectionIsComputedExactlyOncePerDashboardRequest() {
        PeriodDto current = openCycle(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        PeriodDto compare = PeriodDto.of(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 1),
                PeriodType.LAST_PAY_CYCLE);

        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("2500.00")).build());
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(current, compare));
        when(transactionQueryService.totals(WALLET_ID, USER_ID,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 26)))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("5000.00"), new BigDecimal("3100.00")));
        when(transactionQueryService.totals(WALLET_ID, USER_ID,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 26)))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("5000.00"), new BigDecimal("2860.00")));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(LocalDate.of(2026, 5, 26))))
                .thenReturn(emptyFixedTile());
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(LocalDate.of(2026, 5, 26)),
                any()))
                .thenReturn(projection("620.00", "740.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), TODAY, List.of()));
        when(categoryAggregationService.aggregateExpenses(eq(WALLET_ID), eq(USER_ID), any(), any(),
                eq(CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));

        service.getSummary(WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        verify(spendingProjectionService, org.mockito.Mockito.times(1))
                .getSpendingProjection(any(Wallet.class), eq(USER_ID), any(PeriodDto.class),
                        eq(LocalDate.of(2026, 5, 26)), any());
    }

    @Test
    void walletIsLookedUpExactlyOncePerDashboardRequest() {
        PeriodDto current = openCycle(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        PeriodDto compare = PeriodDto.of(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 1),
                PeriodType.LAST_PAY_CYCLE);

        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("2500.00")).build());
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(current, compare));
        when(transactionQueryService.totals(WALLET_ID, USER_ID,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 26)))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("5000.00"), new BigDecimal("3100.00")));
        when(transactionQueryService.totals(WALLET_ID, USER_ID,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 26)))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("5000.00"), new BigDecimal("2860.00")));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(LocalDate.of(2026, 5, 26))))
                .thenReturn(emptyFixedTile());
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(LocalDate.of(2026, 5, 26)),
                any()))
                .thenReturn(projection("620.00", "740.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), TODAY, List.of()));
        when(categoryAggregationService.aggregateExpenses(eq(WALLET_ID), eq(USER_ID), any(), any(),
                eq(CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));

        service.getSummary(WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        verify(walletService, org.mockito.Mockito.times(1))
                .getWalletEntityByIdForUser(WALLET_ID, USER_ID);
    }

    @Test
    void fixedPaymentTileIsComputedExactlyOncePerDashboardRequest() {
        PeriodDto current = openCycle(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        PeriodDto compare = PeriodDto.of(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 1),
                PeriodType.LAST_PAY_CYCLE);

        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("2500.00")).build());
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(current, compare));
        when(transactionQueryService.totals(WALLET_ID, USER_ID,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 26)))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("5000.00"), new BigDecimal("3100.00")));
        when(transactionQueryService.totals(WALLET_ID, USER_ID,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 26)))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("5000.00"), new BigDecimal("2860.00")));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(LocalDate.of(2026, 5, 26))))
                .thenReturn(emptyFixedTile());
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(LocalDate.of(2026, 5, 26)),
                any()))
                .thenReturn(projection("620.00", "740.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), TODAY, List.of()));
        when(categoryAggregationService.aggregateExpenses(eq(WALLET_ID), eq(USER_ID), any(), any(),
                eq(CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));

        service.getSummary(WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        verify(fixedPaymentDashboardService, org.mockito.Mockito.times(1))
                .getFixedPaymentsTileData(any(), any(Wallet.class), eq(USER_ID), eq(LocalDate.of(2026, 5, 26)));
    }

    @Test
    void awaitingSalaryKeepsCountingDaysAndTotalsWithoutVerdictOrProjection() {
        // expected payday May 24 has passed without a salary; today is May 26 (2 days late)
        LocalDate start = LocalDate.of(2026, 4, 24);
        LocalDate expectedPayday = LocalDate.of(2026, 5, 24);
        PeriodDto current = new PeriodDto(start, expectedPayday.minusDays(1), expectedPayday, PeriodType.PAY_CYCLE,
                CycleState.AWAITING_SALARY, expectedPayday, true);
        PeriodDto compare = new PeriodDto(LocalDate.of(2026, 3, 24), LocalDate.of(2026, 4, 23), start,
                PeriodType.LAST_PAY_CYCLE, CycleState.CLOSED, null, true);

        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("410.50")).build());
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(current, compare));
        // totals run to today, i.e. including the two late days after the expected payday
        when(transactionQueryService.totals(WALLET_ID, USER_ID, start, TODAY))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("4000.00"), new BigDecimal("3900.00")));
        when(transactionQueryService.totals(WALLET_ID, USER_ID, compare.startDate(), compare.endDate()))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("4000.00"), new BigDecimal("3000.00")));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(TODAY)))
                .thenReturn(emptyFixedTile());
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(TODAY), any()))
                .thenReturn(reportingProjection(PeriodType.PAY_CYCLE, start, expectedPayday.minusDays(1),
                        SpendingProjectionCalculator.REASON_AWAITING_SALARY));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(), eq(TODAY), any()))
                .thenReturn(new InsightResponseDto(start, TODAY, List.of()));
        when(categoryAggregationService.aggregateExpenses(eq(WALLET_ID), eq(USER_ID), any(), any(), any()))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));

        DashboardSummaryDto result = service.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.period().cycleState()).isEqualTo(CycleState.AWAITING_SALARY);
        assertThat(result.period().expectedPaydayDate()).isEqualTo(expectedPayday);
        assertThat(result.period().cutoffDate()).isEqualTo(TODAY);
        assertThat(result.period().endDate()).isEqualTo(expectedPayday.minusDays(1));
        assertThat(result.period().daysElapsed()).isEqualTo(33);
        assertThat(result.period().daysInPeriod()).isEqualTo(33);
        assertThat(result.period().daysRemaining()).isZero();
        assertThat(result.period().reporting()).isFalse();
        assertThat(result.kpis().expenses().amount()).isEqualByComparingTo("3900.00");
        assertThat(result.cycleHealth()).isNotNull();
        assertThat(result.cycleHealth().status()).isNull();
        assertThat(result.cycleHealth().projectionAvailable()).isFalse();
        assertThat(result.cycleHealth().projectionReason()).isEqualTo(SpendingProjectionCalculator.REASON_AWAITING_SALARY);
        assertThat(result.cycleHealth().currentBalance()).isEqualByComparingTo("410.50");
        assertThat(result.cycleHealth().safeToSpend()).isNull();
        assertThat(result.cycleHealth().safeToSpendPerDay()).isNull();
        assertThat(result.cycleHealth().projectedEndBalance()).isNull();
        assertThat(result.cycleHealth().spendingPaceDeltaPercent()).isNull();
        verify(transactionQueryService).totals(WALLET_ID, USER_ID, start, TODAY);
        // 3.1: the resolved AWAITING_SALARY period is passed through unchanged; the fixed-payment service derives
        // the committed window (through today) from its cycle state, the same way /api/fixed-payments/tile does
        verify(fixedPaymentDashboardService).getFixedPaymentsTileData(
                same(current), any(Wallet.class), eq(USER_ID), eq(TODAY));
    }

    @Test
    void monthlyAndCustomAreReportingOnlyWithKpisEqualToPayCycle() {
        PeriodDto month = PeriodDto.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 1), PeriodType.MONTHLY);
        PeriodDto custom = PeriodDto.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 1), PeriodType.CUSTOM);

        DashboardSummaryDto payCycle = summaryFor(PeriodType.PAY_CYCLE, payCycle(), null, null);
        org.mockito.Mockito.reset(periodService, walletService, transactionQueryService, spendingProjectionService,
                fixedPaymentDashboardService, insightEngine, categoryAggregationService);
        DashboardSummaryDto monthly = summaryFor(PeriodType.MONTHLY, month, null, null);
        org.mockito.Mockito.reset(periodService, walletService, transactionQueryService, spendingProjectionService,
                fixedPaymentDashboardService, insightEngine, categoryAggregationService);
        DashboardSummaryDto customRange = summaryFor(PeriodType.CUSTOM, custom,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));

        assertThat(payCycle.cycleHealth()).isNotNull();
        assertThat(payCycle.fixedPayments()).isNotNull();
        for (DashboardSummaryDto reporting : List.of(monthly, customRange)) {
            assertThat(reporting.cycleHealth()).isNull();
            assertThat(reporting.fixedPayments()).isNull();
            assertThat(reporting.period().reporting()).isTrue();
            assertThat(reporting.period().cycleState()).isNull();
            assertThat(reporting.period().expectedPaydayDate()).isNull();
            assertThat(reporting.period().daysInPeriod()).isEqualTo(31);
            assertThat(reporting.period().daysElapsed()).isEqualTo(26);
            assertThat(reporting.period().daysRemaining()).isEqualTo(5);
            assertThat(reporting.kpis().income().amount()).isEqualByComparingTo(payCycle.kpis().income().amount());
            assertThat(reporting.kpis().expenses().amount()).isEqualByComparingTo(payCycle.kpis().expenses().amount());
            assertThat(reporting.kpis().saved().amount()).isEqualByComparingTo(payCycle.kpis().saved().amount());
            assertThat(reporting.kpis().savingsRate().percent()).isEqualByComparingTo(payCycle.kpis().savingsRate().percent());
        }
        // the tile is not even computed for reporting periods (the last reset leaves only the CUSTOM interactions)
        verify(fixedPaymentDashboardService, never()).getFixedPaymentsTileData(any(), any(Wallet.class), any(), any());
    }

    @Test
    void lastPayCycleIsReportingOnlyButKeepsFixedPaymentsTile() {
        PeriodDto last = new PeriodDto(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 1),
                PeriodType.LAST_PAY_CYCLE, CycleState.CLOSED, null, true);

        DashboardSummaryDto result = summaryFor(PeriodType.LAST_PAY_CYCLE, last, null, null);

        assertThat(result.cycleHealth()).isNull();
        assertThat(result.fixedPayments()).isNotNull();
        assertThat(result.period().reporting()).isTrue();
        assertThat(result.period().cycleState()).isEqualTo(CycleState.CLOSED);
        assertThat(result.period().salaryWallet()).isTrue();
        // a closed cycle is read up to its own end, never into the following cycle
        assertThat(result.period().cutoffDate()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(result.period().daysInPeriod()).isEqualTo(30);
        assertThat(result.period().daysElapsed()).isEqualTo(30);
        assertThat(result.period().daysRemaining()).isZero();
        verify(transactionQueryService).totals(WALLET_ID, USER_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
    }

    @Test
    void savingsWalletGetsCalendarMonthWithoutHealth() {
        // pay-cycle-v2 answers a non-salary wallet's PAY_CYCLE request with the calendar month typed MONTHLY
        PeriodDto savingsMonth = new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 1), PeriodType.MONTHLY, null, null, false);

        DashboardSummaryDto result = summaryFor(PeriodType.PAY_CYCLE, savingsMonth, null, null);

        assertThat(result.period().type()).isEqualTo(PeriodType.MONTHLY);
        assertThat(result.period().salaryWallet()).isFalse();
        assertThat(result.period().reporting()).isTrue();
        assertThat(result.cycleHealth()).isNull();
        assertThat(result.fixedPayments()).isNull();
    }

    @Test
    void payCycleTypeWithSalaryWalletFalseHasNullHealthEvenWhenOpen() {
        // Defensive edge case: cycleHealth() gates on BOTH periodType == PAY_CYCLE AND salaryWallet == true;
        // this isolates the salaryWallet==false branch from the periodType branch exercised by
        // savingsWalletGetsCalendarMonthWithoutHealth (which uses a MONTHLY-typed period instead).
        PeriodDto nonSalaryOpenCycle = new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 1), PeriodType.PAY_CYCLE, CycleState.OPEN, LocalDate.of(2026, 6, 1), false);

        DashboardSummaryDto result = summaryFor(PeriodType.PAY_CYCLE, nonSalaryOpenCycle, null, null);

        assertThat(result.period().type()).isEqualTo(PeriodType.PAY_CYCLE);
        assertThat(result.period().cycleState()).isEqualTo(CycleState.OPEN);
        assertThat(result.period().salaryWallet()).isFalse();
        assertThat(result.cycleHealth()).isNull();
    }

    @Test
    void customRangeEndingInThePastCapsCutoffAtItsEndNotAtToday() {
        // D-4: a period that has already ended (a past CUSTOM range) is read up to its own end,
        // never past it — only AWAITING_SALARY keeps counting past its nominal end. Mirrors
        // lastPayCycleIsReportingOnlyButKeepsFixedPaymentsTile but for CUSTOM (no fixed-payments tile).
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 4, 20);
        PeriodDto pastCustom = PeriodDto.of(start, end, end.plusDays(1), PeriodType.CUSTOM);

        DashboardSummaryDto result = summaryFor(PeriodType.CUSTOM, pastCustom, start, end);

        assertThat(result.period().cutoffDate()).isEqualTo(end);
        assertThat(result.period().daysInPeriod()).isEqualTo(20);
        assertThat(result.period().daysElapsed()).isEqualTo(20);
        assertThat(result.period().daysRemaining()).isZero();
        assertThat(result.cycleHealth()).isNull();
        assertThat(result.fixedPayments()).isNull();
        verify(transactionQueryService).totals(WALLET_ID, USER_ID, start, end);
    }

    /** One dashboard request for {@code primary} with the same stubbed totals regardless of period type. */
    // --- Stage 4.7 / 4.8: Forecast v2 verdict family and the per-request shadow hand-off ---------------

    @Nested
    class ForecastV2 {

        private final ForecastV2Dto v2 = September2026Fixture.forecastV2Dto();

        @Test
        void flagOff_legacyVerdictUnchangedAndV2FieldsAbsent() {
            PeriodDto current = payCycle();
            DashboardSummaryDto result = summaryFor(PeriodType.PAY_CYCLE, current, null, null);

            DashboardCycleHealthDto health = result.cycleHealth();
            assertThat(health.status()).isEqualTo(DashboardHealthStatus.ON_TRACK);
            assertThat(health.safeToSpend()).isEqualByComparingTo("620.00");
            assertThat(health.forecastStatus()).isNull();
            assertThat(health.discretionaryNow()).isNull();
            assertThat(health.discretionaryPerDay()).isNull();
            assertThat(health.expectedEndBalanceTypical()).isNull();
            assertThat(health.expectedEndBalanceLow()).isNull();
            assertThat(health.expectedEndBalanceHigh()).isNull();
            assertThat(health.confidence()).isNull();
            assertThat(health.oneOffCount()).isNull();
            assertThat(health.committed()).isNull();
            // the shadow observer is handed the request exactly once, with the legacy verdict the client sees
            verify(forecastShadowObserver, times(1)).observe(any(Wallet.class), eq(USER_ID), same(current),
                    eq(TODAY), any(FixedTransactionsTileDto.class), any(SpendingProjectionDto.class), eq("ON_TRACK"));
        }

        @Test
        void flagOn_legacyStatusNullAndForecastStatusCarriesTheVerdict() {
            // "flag on" reaches the dashboard as a projection carrying the ForecastService result
            SpendingProjectionDto projection = projection("620.00", "740.00").withForecast(v2);

            DashboardSummaryDto result = summaryFor(PeriodType.PAY_CYCLE, payCycle(), null, null, projection);

            DashboardCycleHealthDto health = result.cycleHealth();
            assertThat(health.status()).isNull();
            assertThat(health.forecastStatus()).isEqualTo(ForecastStatus.FINE);
            assertThat(health.discretionaryNow()).isEqualByComparingTo("5102.62");
            assertThat(health.discretionaryPerDay()).isEqualByComparingTo("212.61");
            assertThat(health.expectedEndBalanceTypical()).isEqualByComparingTo("1232.48");
            assertThat(health.expectedEndBalanceLow()).isEqualByComparingTo("425.42");
            assertThat(health.expectedEndBalanceHigh()).isEqualByComparingTo("2008.30");
            assertThat(health.confidence()).isEqualTo(ForecastConfidence.HIGH);
            assertThat(health.oneOffCount()).isEqualTo(2);
            assertThat(health.committed()).isEqualByComparingTo("794.27");
            // legacy figures remain for the comparison window; only the verdict moved
            assertThat(health.currentBalance()).isEqualByComparingTo("2500.00");
            assertThat(health.safeToSpend()).isEqualByComparingTo("620.00");
            assertThat(health.safeToSpendPerDay()).isEqualByComparingTo("124.00");
            assertThat(health.projectedEndBalance()).isEqualByComparingTo("740.00");
            assertThat(health.projectionAvailable()).isTrue();
            // one hand-off per request; no legacy verdict to report — active v2 is authoritative
            verify(forecastShadowObserver, times(1)).observe(any(Wallet.class), eq(USER_ID), any(PeriodDto.class),
                    eq(TODAY), any(FixedTransactionsTileDto.class), same(projection), isNull());
        }

        @Test
        void flagOn_shortAndTightVerdictsPassThrough() {
            for (ForecastStatus status : List.of(ForecastStatus.TIGHT, ForecastStatus.SHORT)) {
                org.mockito.Mockito.reset(periodService, walletService, transactionQueryService, spendingProjectionService,
                        fixedPaymentDashboardService, insightEngine, categoryAggregationService);
                ForecastV2Dto verdict = new ForecastV2Dto(status, v2.confidence(), v2.discretionaryNow(),
                        v2.discretionaryPerDay(), v2.committed(), v2.expectedVariableRemaining(),
                        v2.expectedVariableRemainingLow(), v2.expectedVariableRemainingHigh(), v2.expectedEndBalanceTypical(),
                        v2.expectedEndBalanceLow(), v2.expectedEndBalanceHigh(), v2.trimmedDailyPace(), v2.rawDailyBurnRate(),
                        v2.historicalTypicalPerDay(), v2.historyWeight(), v2.baselineCyclesUsed(), v2.baselineCycles(),
                        v2.oneOffs(), v2.committedOccurrences(), null);
                DashboardSummaryDto result = summaryFor(PeriodType.PAY_CYCLE, payCycle(), null, null,
                        projection("-1.00", "740.00").withForecast(verdict));
                assertThat(result.cycleHealth().status()).isNull();
                assertThat(result.cycleHealth().forecastStatus()).isEqualTo(status);
            }
        }

        @Test
        void awaitingSalary_noVerdictInEitherFamilyButTheObligationSideIsStated() {
            LocalDate start = LocalDate.of(2026, 4, 24);
            LocalDate expectedPayday = LocalDate.of(2026, 5, 24);
            PeriodDto awaiting = new PeriodDto(start, expectedPayday.minusDays(1), expectedPayday, PeriodType.PAY_CYCLE,
                    CycleState.AWAITING_SALARY, expectedPayday, true);
            ForecastV2Dto shell = new ForecastV2Dto(null, null, new BigDecimal("2318.02"), null, new BigDecimal("181.98"),
                    null, null, null, null, null, null, null, null, null, null, null, List.of(), List.of(), List.of(),
                    SpendingProjectionCalculator.REASON_AWAITING_SALARY);
            SpendingProjectionDto projection = reportingProjection(PeriodType.PAY_CYCLE, start, expectedPayday.minusDays(1),
                    SpendingProjectionCalculator.REASON_AWAITING_SALARY).withForecast(shell);

            DashboardSummaryDto result = summaryFor(PeriodType.PAY_CYCLE, awaiting, null, null, projection);

            DashboardCycleHealthDto health = result.cycleHealth();
            assertThat(health).isNotNull();
            assertThat(health.status()).isNull();
            assertThat(health.forecastStatus()).isNull();
            assertThat(health.confidence()).isNull();
            assertThat(health.projectionReason()).isEqualTo(SpendingProjectionCalculator.REASON_AWAITING_SALARY);
            assertThat(health.projectionAvailable()).isFalse();
            assertThat(health.committed()).isEqualByComparingTo("181.98");
            assertThat(health.discretionaryNow()).isEqualByComparingTo("2318.02");
            assertThat(health.discretionaryPerDay()).isNull();
            assertThat(health.expectedEndBalanceTypical()).isNull();
            assertThat(health.expectedEndBalanceLow()).isNull();
            assertThat(health.expectedEndBalanceHigh()).isNull();
            assertThat(health.oneOffCount()).isZero();
            verify(forecastShadowObserver, times(1)).observe(any(Wallet.class), eq(USER_ID), same(awaiting), eq(TODAY),
                    any(FixedTransactionsTileDto.class), same(projection), isNull());
        }

        @Test
        void reportingPeriods_noForecastVerdictAndTheObserverDecidesTheyAreNotApplicable() {
            PeriodDto month = PeriodDto.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                    LocalDate.of(2026, 6, 1), PeriodType.MONTHLY);
            PeriodDto last = new PeriodDto(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 1),
                    PeriodType.LAST_PAY_CYCLE, CycleState.CLOSED, null, true);
            PeriodDto savingsMonth = new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                    LocalDate.of(2026, 6, 1), PeriodType.MONTHLY, null, null, false);

            assertThat(summaryFor(PeriodType.MONTHLY, month, null, null).cycleHealth()).isNull();
            org.mockito.Mockito.reset(periodService, walletService, transactionQueryService, spendingProjectionService,
                    fixedPaymentDashboardService, insightEngine, categoryAggregationService);
            assertThat(summaryFor(PeriodType.LAST_PAY_CYCLE, last, null, null).cycleHealth()).isNull();
            org.mockito.Mockito.reset(periodService, walletService, transactionQueryService, spendingProjectionService,
                    fixedPaymentDashboardService, insightEngine, categoryAggregationService);
            assertThat(summaryFor(PeriodType.PAY_CYCLE, savingsMonth, null, null).cycleHealth()).isNull();

            // every request hands off exactly once, with no legacy verdict (the observer skips non-applicable periods)
            verify(forecastShadowObserver, times(3)).observe(any(Wallet.class), eq(USER_ID), any(PeriodDto.class), any(),
                    any(), any(SpendingProjectionDto.class), isNull());
        }

        @Test
        void shadowHandOffCarriesTheRealDashboardVerdictIncludingWarning() {
            // 3100 vs 2860 last cycle is +8.39 % (no warning); a 100 % pace delta trips WARNING — the observer must
            // see the dashboard's own verdict, not a projection-only approximation
            PeriodDto current = payCycle();
            PeriodDto compare = PeriodDto.of(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 1),
                    PeriodType.LAST_PAY_CYCLE);
            when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                    .thenReturn(Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("2500.00")).build());
            when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                    .thenReturn(new ResolvedPeriods(current, compare));
            when(transactionQueryService.totals(WALLET_ID, USER_ID, LocalDate.of(2026, 5, 1), TODAY))
                    .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(new BigDecimal("5000.00"), new BigDecimal("3100.00")));
            when(transactionQueryService.totals(WALLET_ID, USER_ID, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 26)))
                    .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(new BigDecimal("5000.00"), new BigDecimal("1550.00")));
            when(fixedPaymentDashboardService.getFixedPaymentsTileData(any(), any(Wallet.class), eq(USER_ID), eq(TODAY)))
                    .thenReturn(fixedTile());
            when(spendingProjectionService.getSpendingProjection(any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(TODAY), any()))
                    .thenReturn(projection("620.00", "740.00"));
            when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(), eq(TODAY), any()))
                    .thenReturn(new InsightResponseDto(current.startDate(), TODAY, List.of()));
            when(categoryAggregationService.aggregateExpenses(eq(WALLET_ID), eq(USER_ID), any(), any(), any()))
                    .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));

            DashboardSummaryDto result = service.getSummary(WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                    CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

            assertThat(result.cycleHealth().status()).isEqualTo(DashboardHealthStatus.WARNING);
            verify(forecastShadowObserver, times(1)).observe(any(Wallet.class), eq(USER_ID), same(current), eq(TODAY),
                    any(FixedTransactionsTileDto.class), any(SpendingProjectionDto.class), eq("WARNING"));
        }
    }

    private DashboardSummaryDto summaryFor(PeriodType requested, PeriodDto primary, LocalDate startDate, LocalDate endDate) {
        SpendingProjectionDto projection = primary.periodType() == PeriodType.PAY_CYCLE
                && primary.cycleState() == CycleState.OPEN
                ? projection("620.00", "740.00")
                : reportingProjection(primary.periodType(), primary.startDate(), primary.endDate(),
                        primary.periodType() == PeriodType.LAST_PAY_CYCLE
                                ? SpendingProjectionCalculator.REASON_CLOSED_CYCLE
                                : SpendingProjectionCalculator.REASON_REPORTING_PERIOD);
        return summaryFor(requested, primary, startDate, endDate, projection);
    }

    private DashboardSummaryDto summaryFor(PeriodType requested, PeriodDto primary, LocalDate startDate, LocalDate endDate,
                                           SpendingProjectionDto projection) {
        boolean tileShown = primary.periodType() == PeriodType.PAY_CYCLE || primary.periodType() == PeriodType.LAST_PAY_CYCLE;
        // a cycle awaiting its salary keeps counting through today; every other period is capped at its end
        LocalDate cutoff = primary.cycleState() != CycleState.AWAITING_SALARY && primary.endDate().isBefore(TODAY)
                ? primary.endDate() : TODAY;
        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("2500.00")).build());
        when(periodService.resolvePeriods(requested, WALLET_ID, USER_ID, startDate, endDate))
                .thenReturn(new ResolvedPeriods(primary, null));
        when(transactionQueryService.totals(WALLET_ID, USER_ID, primary.startDate(), cutoff))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("5000.00"), new BigDecimal("3100.00")));
        if (tileShown) {
            when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                    any(), any(Wallet.class), eq(USER_ID), eq(cutoff)))
                    .thenReturn(fixedTile());
        }
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(cutoff), any()))
                .thenReturn(projection);
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(), eq(cutoff), any()))
                .thenReturn(new InsightResponseDto(primary.startDate(), cutoff, List.of()));
        when(categoryAggregationService.aggregateExpenses(WALLET_ID, USER_ID,
                primary.startDate(), cutoff, CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES))
                .thenReturn(new CategoryAggregationResult(new BigDecimal("3100.00"), List.of(
                        category(1, "Groceries", "3100.00"))));
        return service.getSummary(WALLET_ID, USER_ID, requested, startDate, endDate, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);
    }

    @Test
    void healthStatusUsesDangerAndWarningRules() {
        assertStatusForProjection("-1.00", "100.00", "0.00", DashboardHealthStatus.DANGER);
        assertStatusForProjection("100.00", "-1.00", "0.00", DashboardHealthStatus.DANGER);
        assertStatusForProjection("100.00", "100.00", "100.00", DashboardHealthStatus.WARNING);
    }

    private void assertStatusForProjection(String safeToSpend, String projectedEndBalance,
                                           String compareExpenses, DashboardHealthStatus expected) {
        PeriodDto current = openCycle(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        PeriodDto compare = PeriodDto.of(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 1),
                PeriodType.LAST_PAY_CYCLE);
        org.mockito.Mockito.reset(periodService, walletService, transactionQueryService, spendingProjectionService,
                fixedPaymentDashboardService, insightEngine, categoryAggregationService);
        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("1000.00")).build());
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(current, compare));
        when(transactionQueryService.totals(WALLET_ID, USER_ID, current.startDate(), TODAY))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("1000.00"), new BigDecimal("125.00")));
        when(transactionQueryService.totals(WALLET_ID, USER_ID,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 26)))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("1000.00"), new BigDecimal(compareExpenses)));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(LocalDate.of(2026, 5, 26))))
                .thenReturn(emptyFixedTile());
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(LocalDate.of(2026, 5, 26)),
                any()))
                .thenReturn(projection(safeToSpend, projectedEndBalance));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), TODAY, List.of()));
        when(categoryAggregationService.aggregateExpenses(eq(WALLET_ID), eq(USER_ID), any(), any(),
                eq(CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));

        DashboardSummaryDto result = service.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.cycleHealth().status()).isEqualTo(expected);
    }

    private SpendingProjectionDto projection(String safeToSpend, String projectedEndBalance) {
        // daysRemaining = 5; safeToSpendPerDay = safeToSpend / 5
        BigDecimal safeTotal = new BigDecimal(safeToSpend);
        BigDecimal perDay = safeTotal.divide(new BigDecimal("5"), 2, java.math.RoundingMode.HALF_UP);
        return new SpendingProjectionDto(
                PeriodType.PAY_CYCLE,
                "Current pay cycle",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                31,
                26,
                5,
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("3100.00"),
                new BigDecimal("119.23"),
                new BigDecimal("3696.15"),
                new BigDecimal(projectedEndBalance),
                new BigDecimal("600.00"),
                safeTotal,
                perDay,
                new BigDecimal("3100.00"),
                BigDecimal.ZERO,
                new BigDecimal("119.23"),
                new BigDecimal("596.15"),
                true,
                null,
                null);
    }

    private FixedTransactionsTileDto fixedTile() {
        return new FixedTransactionsTileDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 5, 31),
                null,
                null,
                new FixedSummaryDto(
                        new BigDecimal("1800.00"), 4,
                        new BigDecimal("1200.00"), 1,
                        new BigDecimal("600.00"), 3,
                        BigDecimal.ZERO, 0,
                        new BigDecimal("36.00"),
                        new BigDecimal("1200.00")),
                new FixedProgressDto(1, 4, new BigDecimal("25.00"),
                        LocalDate.of(2026, 5, 27), "Rent", null, null, 4),
                new BigDecimal("2500.00"),
                new BigDecimal("1900.00"),
                new RiskIndicatorDto(false, null),
                List.of(),
                List.of(
                        occurrence(1L, "Rent", "500.00", LocalDate.of(2026, 5, 27)),
                        occurrence(2L, "Phone", "50.00", LocalDate.of(2026, 5, 28)),
                        occurrence(3L, "Internet", "40.00", LocalDate.of(2026, 5, 29)),
                        occurrence(4L, "Gym", "10.00", LocalDate.of(2026, 5, 30))),
                List.of(),
                null,
                List.of());
    }

    private FixedTransactionsTileDto emptyFixedTile() {
        return new FixedTransactionsTileDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 5, 31),
                null,
                null,
                new FixedSummaryDto(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0,
                        BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO),
                new FixedProgressDto(0, 0, BigDecimal.ZERO, null, null, null, null, 0),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new RiskIndicatorDto(false, null),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of());
    }

    private FixedOccurrenceRowDto occurrence(Long id, String title, String amount, LocalDate dueDate) {
        return new FixedOccurrenceRowDto(
                id,
                id.intValue(),
                title,
                1,
                "Bills",
                null,
                WALLET_ID,
                new BigDecimal(amount),
                BigDecimal.ZERO,
                dueDate,
                OccurrenceStatus.PENDING,
                1,
                null,
                null,
                null,
                null,
                null,
                FixedOccurrenceBucket.LATER_THIS_CYCLE);
    }

    private CategoryAggregation category(Integer id, String name, String amount) {
        return new CategoryAggregation(
                id,
                name,
                null,
                new BigDecimal(amount),
                BigDecimal.ZERO,
                1L);
    }

    /** pay-cycle-v2 shape of the salary wallet's open cycle: the whole cycle, next salary expected the day after. */
    private static PeriodDto openCycle(LocalDate start, LocalDate end) {
        return new PeriodDto(start, end, end.plusDays(1), PeriodType.PAY_CYCLE, CycleState.OPEN, end.plusDays(1), true);
    }

    private PeriodDto payCycle() {
        return openCycle(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    }

    private PeriodDto previousCycle() {
        return new PeriodDto(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 1),
                PeriodType.LAST_PAY_CYCLE,
                CycleState.CLOSED,
                null,
                true);
    }

    /** A projection the service returns for reporting periods: actuals only, every projected figure null. */
    private SpendingProjectionDto reportingProjection(PeriodType type, LocalDate start, LocalDate end, String reason) {
        return new SpendingProjectionDto(
                type, "reporting", start, end,
                InclusiveDateRange.daysBetween(start, end), InclusiveDateRange.daysBetween(start, TODAY),
                Math.max(0, InclusiveDateRange.daysBetween(TODAY.plusDays(1), end)),
                new BigDecimal("5000.00"), new BigDecimal("5000.00"), new BigDecimal("3100.00"),
                null, null, null, BigDecimal.ZERO, null, null,
                new BigDecimal("3100.00"), BigDecimal.ZERO, new BigDecimal("119.23"), null,
                false, reason, null);
    }

    private void stubSummaryForCutoff(PeriodDto current, PeriodDto compare,
                                      LocalDate cutoff, LocalDate compareCutoff) {
        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder()
                        .id(WALLET_ID)
                        .name("Main")
                        .balance(new BigDecimal("2500.00"))
                        .build());
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(current, compare));
        when(transactionQueryService.totals(WALLET_ID, USER_ID, current.startDate(), cutoff))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("1000.00"), new BigDecimal("100.00")));
        when(transactionQueryService.totals(WALLET_ID, USER_ID, compare.startDate(), compareCutoff))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("900.00"), new BigDecimal("80.00")));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(cutoff)))
                .thenReturn(emptyFixedTile());
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(cutoff), any()))
                .thenReturn(projection("620.00", "740.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(), eq(cutoff), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), cutoff, List.of()));
        when(categoryAggregationService.aggregateExpenses(WALLET_ID, USER_ID,
                current.startDate(), cutoff, CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES))
                .thenReturn(new CategoryAggregationResult(new BigDecimal("100.00"), List.of(
                        category(1, "Food", "100.00"))));
        when(categoryAggregationService.aggregateExpenses(WALLET_ID, USER_ID,
                compare.startDate(), compareCutoff, CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES))
                .thenReturn(new CategoryAggregationResult(new BigDecimal("80.00"), List.of(
                        category(1, "Food", "80.00"))));
    }

    private void assertInsightWindow(LocalDate cutoff, LocalDate compareCutoff) {
        ArgumentCaptor<PeriodDto> primaryCaptor = ArgumentCaptor.forClass(PeriodDto.class);
        ArgumentCaptor<PeriodDto> compareCaptor = ArgumentCaptor.forClass(PeriodDto.class);
        verify(insightEngine).getInsightsForWindow(
                any(Wallet.class), eq(USER_ID), primaryCaptor.capture(), compareCaptor.capture(),
                eq(cutoff), any(PrecomputedInsightData.class));
        assertThat(primaryCaptor.getValue().startDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(primaryCaptor.getValue().endDate()).isEqualTo(cutoff);
        assertThat(compareCaptor.getValue().startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(compareCaptor.getValue().endDate()).isEqualTo(compareCutoff);
    }
}
