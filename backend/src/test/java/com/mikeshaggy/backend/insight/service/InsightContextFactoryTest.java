package com.mikeshaggy.backend.insight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonCategoryDto;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonResponseDto;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonService;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonStatus;
import com.mikeshaggy.backend.analytics.forecast.ForecastService;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService;
import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService.CategoryDayBaseline;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleVariableTotal;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.UnlinkedExpense;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.insight.detector.InsightContext;
import com.mikeshaggy.backend.insight.detector.InsightContext.FixedPaymentHistory;
import com.mikeshaggy.backend.insight.detector.InsightContext.ImpulseSplit;
import com.mikeshaggy.backend.insight.detector.InsightContext.OccurrenceSnapshot;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.CycleMinimum;
import com.mikeshaggy.backend.ledger.service.BalanceSparklineService;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transfer.service.TransferSummaryService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Stage 7.2 context wiring: every input comes from the service that owns it, nothing is loaded before a detector
 * asks, each input is loaded once, and the Stage 3 tile is shared by the forecast and the fixed-payment rows.
 */
class InsightContextFactoryTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final LocalDate START = LocalDate.of(2026, 10, 9);
    private static final LocalDate NEXT = LocalDate.of(2026, 11, 10);
    private static final PayCycle CYCLE = PayCycle.open(USER, 1, START, NEXT);
    private static final PeriodDto PERIOD = new PeriodDto(START, NEXT.minusDays(1), NEXT, PeriodType.PAY_CYCLE,
            CycleState.OPEN, NEXT, true);

    private ForecastService forecastService;
    private FixedPaymentDashboardService fixedPaymentDashboardService;
    private CycleComparisonService cycleComparisonService;
    private TransferSummaryService transferSummaryService;
    private BalanceSparklineService balanceSparklineService;
    private HistoricalCategorySpendService historicalCategorySpendService;
    private HistoricalVariableSpendService historicalVariableSpendService;
    private AnalyticsTransactionQueryService transactionQueryService;
    private WalletRepository walletRepository;
    private FixedPaymentRepository fixedPaymentRepository;
    private FixedPaymentOccurrenceRepository occurrenceRepository;
    private Wallet wallet;
    private FixedTransactionsTileDto tile;
    private FixedOccurrenceRowDto overdue;
    private FixedOccurrenceRowDto upcoming;
    private FixedOccurrenceRowDto paid;
    private FixedOccurrenceRowDto afterPayday;

    @BeforeEach
    void setUp() {
        forecastService = mock(ForecastService.class);
        fixedPaymentDashboardService = mock(FixedPaymentDashboardService.class);
        cycleComparisonService = mock(CycleComparisonService.class);
        transferSummaryService = mock(TransferSummaryService.class);
        balanceSparklineService = mock(BalanceSparklineService.class);
        historicalCategorySpendService = mock(HistoricalCategorySpendService.class);
        historicalVariableSpendService = mock(HistoricalVariableSpendService.class);
        transactionQueryService = mock(AnalyticsTransactionQueryService.class);
        walletRepository = mock(WalletRepository.class);
        fixedPaymentRepository = mock(FixedPaymentRepository.class);
        occurrenceRepository = mock(FixedPaymentOccurrenceRepository.class);
        wallet = Wallet.builder().id(1).name("Daily").balance(new BigDecimal("5896.89")).build();

        overdue = mock(FixedOccurrenceRowDto.class);
        upcoming = mock(FixedOccurrenceRowDto.class);
        paid = mock(FixedOccurrenceRowDto.class);
        afterPayday = mock(FixedOccurrenceRowDto.class);
        tile = mock(FixedTransactionsTileDto.class);
        when(tile.overdue()).thenReturn(List.of(overdue));
        when(tile.upcoming()).thenReturn(List.of(upcoming));
        when(tile.paid()).thenReturn(List.of(paid));
        when(tile.afterPayday()).thenReturn(null);
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(PERIOD, wallet, USER, TODAY)).thenReturn(tile);
        when(forecastService.isApplicable(PERIOD)).thenReturn(true);
    }

    @Test
    void creatingTheContext_loadsNothing_andCarriesCycleWalletAndDay() {
        InsightContext context = factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY);

        assertThat(context.userId()).isEqualTo(USER);
        assertThat(context.cycle()).isEqualTo(CYCLE);
        assertThat(context.today()).isEqualTo(TODAY);
        assertThat(context.wallet()).isEqualTo(new InsightContext.WalletSnapshot(1, "Daily", new BigDecimal("5896.89")));
        verifyNoInteractions(forecastService, fixedPaymentDashboardService, cycleComparisonService,
                transferSummaryService, balanceSparklineService, historicalCategorySpendService,
                historicalVariableSpendService, transactionQueryService, walletRepository, fixedPaymentRepository,
                occurrenceRepository);
    }

    @Test
    void fixedPaymentRows_areEveryTileBucket_andTheTileIsSharedWithTheForecast() {
        ForecastV2Dto forecast = mock(ForecastV2Dto.class);
        when(forecastService.forecast(wallet, USER, PERIOD, TODAY, tile)).thenReturn(forecast);
        InsightContext context = factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY);

        assertThat(context.fixedPaymentRows()).containsExactly(overdue, upcoming, paid);
        assertThat(context.forecast()).containsSame(forecast);
        assertThat(context.fixedPaymentRows()).hasSize(3);
        assertThat(context.forecast()).containsSame(forecast);

        verify(fixedPaymentDashboardService, times(1)).getFixedPaymentsTileData(any(), any(Wallet.class), any(), any());
        verify(forecastService, times(1)).forecast(wallet, USER, PERIOD, TODAY, tile);
    }

    @Test
    void forecast_withForecastV2Off_isEmpty_andNotComputed() {
        InsightContext context = factory(false).create(USER, wallet, CYCLE, PERIOD, TODAY);

        assertThat(context.forecast()).isEmpty();
        verify(forecastService, never()).forecast(any(), any(), any(), any(), any());
        verifyNoInteractions(fixedPaymentDashboardService);
    }

    @Test
    void forecast_notApplicable_isEmpty() {
        when(forecastService.isApplicable(PERIOD)).thenReturn(false);

        assertThat(factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY).forecast()).isEmpty();
        verify(forecastService, never()).forecast(any(), any(), any(), any(), any());
    }

    @Test
    void forecast_nullFromService_isEmpty_notZero() {
        when(forecastService.forecast(wallet, USER, PERIOD, TODAY, tile)).thenReturn(null);

        assertThat(factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY).forecast()).isEmpty();
    }

    @Test
    void categoryBaselines_comeFromCycleComparison_withSixCycles_asOfToday() {
        CycleComparisonCategoryDto groceries = new CycleComparisonCategoryDto(3, "Groceries", null,
                new BigDecimal("314.84"), new BigDecimal("162.34"), new BigDecimal("152.50"), new BigDecimal("93.94"),
                5, new BigDecimal("4.00"), CycleComparisonStatus.ABOVE_BASELINE);
        CycleComparisonResponseDto comparison = mock(CycleComparisonResponseDto.class);
        when(comparison.categories()).thenReturn(List.of(groceries));
        when(cycleComparisonService.getCycleComparison(1, USER, TODAY, 6, null, CategoryAggregationMode.ALL, null))
                .thenReturn(comparison);
        InsightContext context = factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY);

        assertThat(context.categoryBaselines()).containsExactly(groceries);
        assertThat(context.categoryBaselines()).containsExactly(groceries);
        verify(cycleComparisonService, times(1)).getCycleComparison(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void transferSummary_andSparklineMinima_comeFromTheStage6Services_onceEach() {
        TransferSummaryResponse transfers = new TransferSummaryResponse(1, null, List.of(), List.of());
        when(transferSummaryService.getSummary(1, USER, TransferSummaryService.DEFAULT_CYCLES)).thenReturn(transfers);
        CycleMinimum minimum = new CycleMinimum(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9),
                new BigDecimal("-93.29"), LocalDate.of(2026, 7, 30));
        when(balanceSparklineService.getSparkline(1, USER, BalanceSparklineService.DEFAULT_DAYS))
                .thenReturn(new BalanceSparklineResponse(1, TODAY.minusDays(89), TODAY, List.of(), List.of(),
                        List.of(minimum)));
        InsightContext context = factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY);

        assertThat(context.transferSummary()).isSameAs(transfers);
        assertThat(context.transferSummary()).isSameAs(transfers);
        assertThat(context.sparklineMinima()).containsExactly(minimum);
        assertThat(context.sparklineMinima()).containsExactly(minimum);
        verify(transferSummaryService, times(1)).getSummary(any(), any(), eq(TransferSummaryService.DEFAULT_CYCLES));
        verify(balanceSparklineService, times(1)).getSparkline(any(), any(), eq(BalanceSparklineService.DEFAULT_DAYS));
    }

    @Test
    void failingTile_isLoadedOnce_andFailsBothDependentInputs() {
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(PERIOD, wallet, USER, TODAY))
                .thenThrow(new IllegalStateException("tile failed"));
        InsightContext context = factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY);

        assertThatThrownBy(context::fixedPaymentRows).hasMessage("tile failed");
        assertThatThrownBy(context::forecast).hasMessage("tile failed");
        assertThatThrownBy(context::fixedPaymentRows).hasMessage("tile failed");
        verify(fixedPaymentDashboardService, times(1)).getFixedPaymentsTileData(any(), any(Wallet.class), any(), any());
    }

    @Test
    void sparkline_isLoadedOnce_forBothTheFullResponseAndTheMinima() {
        BalanceSparklineResponse sparkline = new BalanceSparklineResponse(1, TODAY.minusDays(89), TODAY, List.of(),
                List.of(), List.of());
        when(balanceSparklineService.getSparkline(1, USER, BalanceSparklineService.DEFAULT_DAYS)).thenReturn(sparkline);
        InsightContext context = factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY);

        assertThat(context.sparkline()).isSameAs(sparkline);
        assertThat(context.sparklineMinima()).isEmpty();
        assertThat(context.sparkline()).isSameAs(sparkline);
        verify(balanceSparklineService, times(1)).getSparkline(any(), any(), any(Integer.class));
    }

    @Test
    void stage73Inputs_comeFromTheirCanonicalServices_onceEach_withTheCurrentCycleAndDay() {
        CategoryDayBaseline baseline = new CategoryDayBaseline(14, List.of(), List.of());
        when(historicalCategorySpendService.atDay(USER, 1, CYCLE, TODAY)).thenReturn(baseline);
        List<CycleVariableTotal> history = List.of(new CycleVariableTotal(
                PayCycle.closed(USER, 1, LocalDate.of(2026, 9, 9), START.minusDays(1)), new BigDecimal("3600.00")));
        when(historicalVariableSpendService.closedCycleTotals(USER, 1, HistoricalVariableSpendService.MAX_TOTAL_CYCLES))
                .thenReturn(history);
        when(transactionQueryService.sumUnlinkedForPace(1, USER, START, TODAY)).thenReturn(new BigDecimal("1500.00"));
        when(transactionQueryService.sumUnlinkedForPaceByImportance(1, USER, START, TODAY, Importance.SHOULDNT_HAVE))
                .thenReturn(new BigDecimal("320.00"));
        List<UnlinkedExpense> expenses = List.of(new UnlinkedExpense(7L, TODAY, "FX_STREAM", 4, "subs",
                new BigDecimal("49.99")));
        when(transactionQueryService.unlinkedExpenseRows(1, USER, TODAY.minusDays(209), TODAY)).thenReturn(expenses);
        InsightContext context = factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY);

        assertThat(context.categoryDayBaseline()).isSameAs(baseline);
        assertThat(context.cycleHistory()).isEqualTo(history);
        assertThat(context.impulseSplit())
                .isEqualTo(new ImpulseSplit(new BigDecimal("1500.00"), new BigDecimal("320.00")));
        assertThat(context.expenseHistory()).isEqualTo(expenses);
        context.categoryDayBaseline();
        context.cycleHistory();
        context.impulseSplit();
        context.expenseHistory();
        verify(historicalCategorySpendService, times(1)).atDay(any(), any(), any(), any());
        verify(historicalVariableSpendService, times(1)).closedCycleTotals(any(), any(), any(Integer.class));
        verify(transactionQueryService, times(1)).sumUnlinkedForPace(any(), any(), any(), any());
        verify(transactionQueryService, times(1)).unlinkedExpenseRows(any(), any(), any(), any());
    }

    @Test
    void savingsWallet_isTheOnlyOtherNonFundWallet_elseEmpty() {
        Wallet savings = Wallet.builder().id(2).name("Savings").balance(new BigDecimal("460.01")).build();
        when(walletRepository.findByUserIdAndIsFundFalse(USER)).thenReturn(List.of(wallet, savings));
        assertThat(factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY).savingsWallet())
                .contains(new InsightContext.WalletSnapshot(2, "Savings", new BigDecimal("460.01")));

        Wallet other = Wallet.builder().id(3).name("Other").balance(BigDecimal.ONE).build();
        when(walletRepository.findByUserIdAndIsFundFalse(USER)).thenReturn(List.of(wallet, savings, other));
        assertThat(factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY).savingsWallet()).isEmpty();
    }

    @Test
    void fixedPayments_areTheActiveOnes_withSnapshotsOfTheirOccurrences() {
        Category category = Category.builder().id(4).name("subs").build();
        FixedPayment active = FixedPayment.builder().id(10).title("FX_SUB_A").amount(new BigDecimal("105.00"))
                .category(category).build();
        FixedPayment ended = FixedPayment.builder().id(11).title("FX_OLD").amount(BigDecimal.TEN)
                .category(category).activeTo(TODAY.minusDays(1)).build();
        when(fixedPaymentRepository.findAllByWalletIdAndUserId(1, USER)).thenReturn(List.of(ended, active));
        FixedPaymentOccurrence paid = FixedPaymentOccurrence.builder().id(100L).fixedPayment(active)
                .dueDate(LocalDate.of(2026, 9, 25)).expectedAmount(new BigDecimal("105.00"))
                .paidAmount(new BigDecimal("119.00")).status(OccurrenceStatus.PAID).build();
        when(occurrenceRepository.findAllByFixedPaymentIdsAndDueDateBetween(List.of(10), TODAY.minusMonths(25),
                TODAY.plusDays(62))).thenReturn(List.of(paid));

        List<FixedPaymentHistory> payments = factory(true).create(USER, wallet, CYCLE, PERIOD, TODAY).fixedPayments();

        assertThat(payments).containsExactly(new FixedPaymentHistory(10, "FX_SUB_A", new BigDecimal("105.00"), 4,
                List.of(new OccurrenceSnapshot(100L, LocalDate.of(2026, 9, 25), OccurrenceStatus.PAID,
                        new BigDecimal("105.00"), new BigDecimal("119.00")))));
    }

    @Test
    void stage72FiveInputSources_leaveStage73InputsUnavailable_neverZero() {
        InsightContext context = new InsightContext(USER, new InsightContext.WalletSnapshot(1, "Daily", BigDecimal.ONE),
                CYCLE, TODAY, new InsightContext.Sources(java.util.Optional::empty, List::of, () -> null, List::of,
                List::of));

        assertThatThrownBy(context::impulseSplit).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("impulseSplit");
        assertThatThrownBy(context::cycleHistory).isInstanceOf(IllegalStateException.class);
    }

    private InsightContextFactory factory(boolean forecastV2) {
        return new InsightContextFactory(forecastService, fixedPaymentDashboardService, cycleComparisonService,
                transferSummaryService, balanceSparklineService, historicalCategorySpendService,
                historicalVariableSpendService, transactionQueryService,
                new InsightInputQueries(walletRepository, fixedPaymentRepository, occurrenceRepository),
                new FeatureFlags(true, forecastV2, false, false, true));
    }
}
