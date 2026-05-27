package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregation;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.insight.PrecomputedInsightData;
import com.mikeshaggy.backend.analytics.insight.InsightDto;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.analytics.insight.InsightResponseDto;
import com.mikeshaggy.backend.analytics.insight.InsightSeverity;
import com.mikeshaggy.backend.analytics.insight.InsightType;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.dashboard.dto.DashboardCategoryDirection;
import com.mikeshaggy.backend.dashboard.dto.DashboardHealthStatus;
import com.mikeshaggy.backend.dashboard.dto.DashboardSummaryDto;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedProgressDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.RiskIndicatorDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardSummaryServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Integer WALLET_ID = 1;
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
    private FixedPaymentDashboardService fixedPaymentDashboardService;

    @Mock
    private InsightEngine insightEngine;

    @Mock
    private com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService categoryAggregationService;

    private DashboardSummaryService service;

    @BeforeEach
    void setUp() {
        service = new DashboardSummaryService(
                periodService,
                walletService,
                transactionQueryService,
                spendingProjectionService,
                fixedPaymentDashboardService,
                insightEngine,
                categoryAggregationService,
                CLOCK);
    }

    @Test
    void payCycleSummaryUsesSameCutoffComparisonAndMapsAllSections() {
        PeriodDto current = new PeriodDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 1),
                PeriodType.PAY_CYCLE);
        PeriodDto compare = new PeriodDto(
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
                any(BigDecimal.class)))
                .thenReturn(projection("620.00", "740.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), current.endDate(), List.of(
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
                eq(new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 5, 31), PeriodType.PAY_CYCLE)),
                any(Wallet.class),
                eq(USER_ID),
                eq(LocalDate.of(2026, 5, 26)));
    }

    @Test
    void noComparisonKeepsCurrentValuesAndNullDeltas() {
        PeriodDto current = new PeriodDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 1),
                PeriodType.PAY_CYCLE);

        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("1000.00")).build());
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(current, null));
        when(transactionQueryService.totals(WALLET_ID, USER_ID, current.startDate(), current.endDate()))
                .thenReturn(new AnalyticsTransactionQueryService.PeriodTotals(
                        new BigDecimal("0.00"), new BigDecimal("100.00")));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(LocalDate.of(2026, 5, 26))))
                .thenReturn(emptyFixedTile());
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(LocalDate.of(2026, 5, 26)),
                any(BigDecimal.class)))
                .thenReturn(projection("100.00", "900.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), current.endDate(), List.of()));
        when(categoryAggregationService.aggregateExpenses(WALLET_ID, USER_ID,
                current.startDate(), current.endDate(), CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES))
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
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(cutoff), any(BigDecimal.class));
        verify(fixedPaymentDashboardService).getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(cutoff));
        assertInsightWindow(cutoff, compareCutoff);
    }

    @Test
    void asOfDateAfterPeriodEndIsClampedForProjectionAndAggregates() {
        PeriodDto current = payCycle();
        PeriodDto compare = previousCycle();
        LocalDate cutoff = LocalDate.of(2026, 5, 31);
        LocalDate compareCutoff = LocalDate.of(2026, 4, 30);
        stubSummaryForCutoff(current, compare, cutoff, compareCutoff);

        DashboardSummaryDto result = service.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null,
                LocalDate.of(2026, 6, 20),
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.period().asOfDate()).isEqualTo(cutoff);
        assertThat(result.period().cutoffDate()).isEqualTo(cutoff);
        verify(transactionQueryService).totals(WALLET_ID, USER_ID, current.startDate(), cutoff);
        verify(spendingProjectionService).getSpendingProjection(
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(cutoff), any(BigDecimal.class));
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
        PeriodDto current = new PeriodDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 1),
                PeriodType.PAY_CYCLE);
        PeriodDto compare = new PeriodDto(
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
                any(BigDecimal.class)))
                .thenReturn(projection("620.00", "740.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), current.endDate(), List.of()));
        when(categoryAggregationService.aggregateExpenses(eq(WALLET_ID), eq(USER_ID), any(), any(),
                eq(CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));

        service.getSummary(WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        verify(spendingProjectionService, org.mockito.Mockito.times(1))
                .getSpendingProjection(any(Wallet.class), eq(USER_ID), any(PeriodDto.class),
                        eq(LocalDate.of(2026, 5, 26)), any(BigDecimal.class));
    }

    @Test
    void walletIsLookedUpExactlyOncePerDashboardRequest() {
        PeriodDto current = new PeriodDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 1),
                PeriodType.PAY_CYCLE);
        PeriodDto compare = new PeriodDto(
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
                any(BigDecimal.class)))
                .thenReturn(projection("620.00", "740.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), current.endDate(), List.of()));
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
        PeriodDto current = new PeriodDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 1),
                PeriodType.PAY_CYCLE);
        PeriodDto compare = new PeriodDto(
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
                any(BigDecimal.class)))
                .thenReturn(projection("620.00", "740.00"));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), current.endDate(), List.of()));
        when(categoryAggregationService.aggregateExpenses(eq(WALLET_ID), eq(USER_ID), any(), any(),
                eq(CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));

        service.getSummary(WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        verify(fixedPaymentDashboardService, org.mockito.Mockito.times(1))
                .getFixedPaymentsTileData(any(), any(Wallet.class), eq(USER_ID), eq(LocalDate.of(2026, 5, 26)));
    }

    @Test
    void healthStatusUsesDangerAndWarningRules() {
        assertStatusForProjection("-1.00", "100.00", "0.00", DashboardHealthStatus.DANGER);
        assertStatusForProjection("100.00", "-1.00", "0.00", DashboardHealthStatus.DANGER);
        assertStatusForProjection("100.00", "100.00", "100.00", DashboardHealthStatus.WARNING);
    }

    private void assertStatusForProjection(String safeToSpend, String projectedEndBalance,
                                           String compareExpenses, DashboardHealthStatus expected) {
        PeriodDto current = new PeriodDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 1),
                PeriodType.PAY_CYCLE);
        PeriodDto compare = new PeriodDto(
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
        when(transactionQueryService.totals(WALLET_ID, USER_ID, current.startDate(), current.endDate()))
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
                any(BigDecimal.class)))
                .thenReturn(projection(safeToSpend, projectedEndBalance));
        when(insightEngine.getInsightsForWindow(any(Wallet.class), eq(USER_ID), any(), any(),
                eq(LocalDate.of(2026, 5, 26)), any()))
                .thenReturn(new InsightResponseDto(current.startDate(), current.endDate(), List.of()));
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
                true,
                null);
    }

    private FixedTransactionsTileDto fixedTile() {
        return new FixedTransactionsTileDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 5, 31),
                new FixedSummaryDto(
                        new BigDecimal("1800.00"), 4,
                        new BigDecimal("1200.00"), 1,
                        new BigDecimal("600.00"), 3,
                        BigDecimal.ZERO, 0,
                        new BigDecimal("36.00")),
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
                List.of());
    }

    private FixedTransactionsTileDto emptyFixedTile() {
        return new FixedTransactionsTileDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 5, 31),
                new FixedSummaryDto(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0,
                        BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO),
                new FixedProgressDto(0, 0, BigDecimal.ZERO, null, null, null, null, 0),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new RiskIndicatorDto(false, null),
                List.of(),
                List.of(),
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
                null);
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

    private PeriodDto payCycle() {
        return new PeriodDto(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 1),
                PeriodType.PAY_CYCLE);
    }

    private PeriodDto previousCycle() {
        return new PeriodDto(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 1),
                PeriodType.LAST_PAY_CYCLE);
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
                any(Wallet.class), eq(USER_ID), any(PeriodDto.class), eq(cutoff), any(BigDecimal.class)))
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
