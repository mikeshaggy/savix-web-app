package com.mikeshaggy.backend.analytics.insight;

import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.PeriodTotals;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.repository.CategoryBreakdownProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightEngineTest {

    private static final Integer WALLET_ID = 1;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // Primary: March 2026 | Compare: February 2026
    private static final LocalDate PRIMARY_START = LocalDate.of(2026, 3, 1);
    private static final LocalDate PRIMARY_END   = LocalDate.of(2026, 3, 31);
    private static final LocalDate COMPARE_START = LocalDate.of(2026, 2, 1);
    private static final LocalDate COMPARE_END   = LocalDate.of(2026, 2, 28);

    @Mock private TransactionRepository transactionRepository;
    @Mock private AnalyticsTransactionQueryService transactionQueryService;
    @Mock private WalletService walletService;
    @Mock private SpendingProjectionService spendingProjectionService;
    @Mock private PeriodService periodService;

    private InsightEngine insightEngine;

    @BeforeEach
    void setUp() {
        insightEngine = engineAt("2026-04-15T10:00:00Z");
        lenient().when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).build());
        // Default period resolution
        lenient().when(periodService.resolvePeriods(
                        eq(PeriodType.CUSTOM), eq(WALLET_ID), eq(USER_ID), any(), any()))
                .thenReturn(new ResolvedPeriods(
                        new PeriodDto(PRIMARY_START, PRIMARY_END, PRIMARY_END, PeriodType.CUSTOM),
                        new PeriodDto(COMPARE_START, COMPARE_END, COMPARE_END, PeriodType.CUSTOM)));
        // Default: zero income/expenses for all date-range sum queries
        lenient().when(transactionQueryService.totals(
                        eq(WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new PeriodTotals(BigDecimal.ZERO, BigDecimal.ZERO));
        lenient().when(transactionQueryService.sum(
                        eq(WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(BigDecimal.ZERO);
        // Default: no category data
        lenient().when(transactionRepository.findIncludedCategorySpendByWalletUserAndDateRange(
                        eq(WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(List.of());
        // Default: no impulse spend
        lenient().when(transactionQueryService.expenseByImportance(
                        eq(WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class),
                        eq(Importance.SHOULDNT_HAVE)))
                .thenReturn(BigDecimal.ZERO);
        // Default: positive safe-to-spend (no alert)
        lenient().when(spendingProjectionService.getSpendingProjection(
                        eq(WALLET_ID), eq(USER_ID), eq(PeriodType.CUSTOM),
                        any(LocalDate.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(projection("250.00"));
    }

    // ─── category spike ──────────────────────────────────────────────────────────

    @Test
    void categorySpikeGeneratedWhenCurrentExceedsCompareByThirtyPercent() {
        // Primary: 620, Compare: 400 → 620 > 400 * 1.30 = 520 ✓
        when(transactionRepository.findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, PRIMARY_START, PRIMARY_END, CategoryType.EXPENSE))
                .thenReturn(List.of(category(7, "Dining & Restaurants", "620.00")));
        when(transactionRepository.findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, COMPARE_START, COMPARE_END, CategoryType.EXPENSE))
                .thenReturn(List.of(category(7, "Dining & Restaurants", "400.00")));

        InsightResponseDto result = getInsights();

        InsightDto insight = onlyInsightOfType(result, InsightType.CATEGORY_SPIKE);
        assertThat(insight.severity()).isEqualTo(InsightSeverity.WARN);
        assertThat(insight.relatedCategoryId()).isEqualTo(7);
        assertThat(insight.relatedAmount()).isEqualByComparingTo("620.00");
        assertThat(insight.description()).contains("previous period");
        verify(transactionRepository).findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, PRIMARY_START, PRIMARY_END, CategoryType.EXPENSE);
        verify(transactionRepository).findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, COMPARE_START, COMPARE_END, CategoryType.EXPENSE);
    }

    @Test
    void categorySpikeRequiresBaselineInComparePeriodToBeNonZero() {
        // Current has spend, compare has zero → no spike
        when(transactionRepository.findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, PRIMARY_START, PRIMARY_END, CategoryType.EXPENSE))
                .thenReturn(List.of(category(7, "Dining", "620.00")));
        // compare returns empty (default)

        InsightResponseDto result = getInsights();

        assertThat(result.insights()).extracting(InsightDto::type)
                .doesNotContain(InsightType.CATEGORY_SPIKE);
    }

    // ─── impulse spending ────────────────────────────────────────────────────────

    @Test
    void highImpulseSpendingGeneratedWhenShouldntHaveShareIsAboveTwentyPercent() {
        stubPrimaryTotals("2000.00", "1000.00");
        when(transactionQueryService.expenseByImportance(
                WALLET_ID, USER_ID, PRIMARY_START, PRIMARY_END, Importance.SHOULDNT_HAVE))
                .thenReturn(new BigDecimal("250.00")); // 25% of 1000

        InsightResponseDto result = getInsights();

        InsightDto insight = onlyInsightOfType(result, InsightType.HIGH_IMPULSE_SPENDING);
        assertThat(insight.severity()).isEqualTo(InsightSeverity.WARN);
        assertThat(insight.relatedAmount()).isEqualByComparingTo("250.00");
        assertThat(insight.description()).contains("25%");
    }

    // ─── spending pace ───────────────────────────────────────────────────────────

    @Test
    void spendingPaceAboveBaselineGeneratedWhenDailyBurnRateIsTwentyPercentHigher() {
        // Primary March: expenses 4000 over 31 days → 129.03/day
        // Compare Feb: expenses 2800 over 28 days → 100/day
        // 129.03 > 100 * 1.20 = 120 ✓
        stubPrimaryTotals("10000.00", "4000.00");
        stubCompareExpenses("2800.00");
        // pace query: primary start to primary end (today = Apr 15 > Mar 31)
        when(transactionQueryService.sum(
                WALLET_ID, USER_ID, PRIMARY_START, PRIMARY_END, CategoryType.EXPENSE))
                .thenReturn(new BigDecimal("4000.00"));

        InsightResponseDto result = getInsights();

        InsightDto insight = onlyInsightOfType(result, InsightType.SPENDING_PACE_ABOVE_BASELINE);
        assertThat(insight.severity()).isEqualTo(InsightSeverity.WARN);
        assertThat(insight.relatedAmount()).isEqualByComparingTo("4000.00");
        assertThat(insight.description()).contains("previous period");
    }

    @Test
    void spendingPaceNotGeneratedWhenComparePeriodHasNoExpenses() {
        stubPrimaryTotals("5000.00", "3000.00");
        // compareExpenses = 0 (default)

        InsightResponseDto result = getInsights();

        assertThat(result.insights()).extracting(InsightDto::type)
                .doesNotContain(InsightType.SPENDING_PACE_ABOVE_BASELINE);
    }

    // ─── low savings rate ────────────────────────────────────────────────────────

    @Test
    void lowSavingsRateGeneratedWhenIncomeExistsAndSavingsRateIsBelowTenPercent() {
        // income=1000, expenses=950 → rate=5% < 10% threshold
        stubPrimaryTotals("1000.00", "950.00");

        InsightResponseDto result = getInsights();

        InsightDto insight = onlyInsightOfType(result, InsightType.LOW_SAVINGS_RATE);
        assertThat(insight.severity()).isEqualTo(InsightSeverity.ALERT);
        assertThat(insight.relatedAmount()).isNull();
        assertThat(insight.description()).contains("5%");
    }

    @Test
    void incomeOfZeroDoesNotGenerateLowSavingsRate() {
        stubPrimaryTotals("0.00", "200.00");

        InsightResponseDto result = getInsights();

        assertThat(result.insights()).extracting(InsightDto::type)
                .doesNotContain(InsightType.LOW_SAVINGS_RATE);
    }

    // ─── safe-to-spend warning ───────────────────────────────────────────────────

    @Test
    void safeToSpendWarningGeneratedWhenProjectionIsNegativeAndTodayIsInPeriod() {
        insightEngine = engineAt("2026-03-15T10:00:00Z");
        when(spendingProjectionService.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.CUSTOM, PRIMARY_START, PRIMARY_END, LocalDate.of(2026, 3, 15)))
                .thenReturn(projection("-75.00"));

        InsightResponseDto result = getInsights();

        InsightDto insight = onlyInsightOfType(result, InsightType.SAFE_TO_SPEND_WARNING);
        assertThat(insight.severity()).isEqualTo(InsightSeverity.ALERT);
        assertThat(insight.relatedAmount()).isEqualByComparingTo("-75.00");
    }

    @Test
    void safeToSpendWarningNotGeneratedWhenTodayIsOutsidePeriod() {
        // Engine is at 2026-04-15, primary period ends 2026-03-31 → today is outside.
        // spendingProjectionService must NOT be called in this case.

        InsightResponseDto result = getInsights();

        assertThat(result.insights()).extracting(InsightDto::type)
                .doesNotContain(InsightType.SAFE_TO_SPEND_WARNING);
    }

    // ─── good month ──────────────────────────────────────────────────────────────

    @Test
    void goodMonthGeneratedWhenExpensesBelowComparePeriodAndSavingsRateIsStrong() {
        // expenses=800 < compare=1000 * 0.90 = 900 ✓, savingsRate=(2000-800)/2000=60% > 30% ✓
        stubPrimaryTotals("2000.00", "800.00");
        stubCompareExpenses("1000.00");

        InsightResponseDto result = getInsights();

        InsightDto insight = onlyInsightOfType(result, InsightType.GOOD_MONTH);
        assertThat(insight.severity()).isEqualTo(InsightSeverity.INFO);
        assertThat(insight.relatedAmount()).isEqualByComparingTo("1200.00"); // saved = 2000-800
        assertThat(insight.description()).contains("previous period");
    }

    @Test
    void goodMonthNotGeneratedWhenComparePeriodHasNoExpenses() {
        stubPrimaryTotals("2000.00", "800.00");
        // compare expenses = 0 (default) → no good month

        InsightResponseDto result = getInsights();

        assertThat(result.insights()).extracting(InsightDto::type)
                .doesNotContain(InsightType.GOOD_MONTH);
    }

    // ─── general ─────────────────────────────────────────────────────────────────

    @Test
    void noTransactionsReturnsEmptyInsightListWithPeriodDates() {
        InsightResponseDto result = getInsights();

        assertThat(result.periodStart()).isEqualTo(PRIMARY_START);
        assertThat(result.periodEnd()).isEqualTo(PRIMARY_END);
        assertThat(result.insights()).isEmpty();
    }

    @Test
    void normalPeriodWithExpensesMatchingComparePeriodGeneratesNoInsights() {
        // income=2000, expenses=1000, compare=1000 → no spikes, pace ok, savings=50%>10%>30% (pace only)
        // Pace: 1000/31 ≈ 32.26, compare: 1000/28 ≈ 35.71. 32.26 < 35.71*1.2=42.86 → no pace ✓
        stubPrimaryTotals("2000.00", "1000.00");
        stubCompareExpenses("1000.00");
        // pace sum for primary
        when(transactionQueryService.sum(
                WALLET_ID, USER_ID, PRIMARY_START, PRIMARY_END, CategoryType.EXPENSE))
                .thenReturn(new BigDecimal("1000.00"));

        InsightResponseDto result = getInsights();

        // good month: expenses=1000, compare=1000, 1000 < 1000*0.90=900? No → no good month ✓
        // savings rate: 50% >= 10% → no low savings ✓
        assertThat(result.insights()).isEmpty();
    }

    @Test
    void dashboardWindowUsesExplicitCutoffDatesAndAsOfDate() {
        PeriodDto primaryWindow = new PeriodDto(
                PRIMARY_START, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), PeriodType.CUSTOM);
        PeriodDto compareWindow = new PeriodDto(
                COMPARE_START, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 10), PeriodType.CUSTOM);
        LocalDate asOfDate = LocalDate.of(2026, 3, 10);
        SpendingProjectionDto precomputedProjection = projection("250.00");

        when(transactionQueryService.totals(WALLET_ID, USER_ID, PRIMARY_START, LocalDate.of(2026, 3, 10)))
                .thenReturn(new PeriodTotals(new BigDecimal("1000.00"), new BigDecimal("300.00")));
        when(transactionQueryService.sum(WALLET_ID, USER_ID,
                COMPARE_START, LocalDate.of(2026, 2, 10), CategoryType.EXPENSE))
                .thenReturn(new BigDecimal("300.00"));

        Wallet wallet = Wallet.builder().id(WALLET_ID).build();
        InsightResponseDto result = insightEngine.getInsightsForWindow(
                wallet, USER_ID, primaryWindow, compareWindow, asOfDate, precomputedProjection);

        assertThat(result.periodStart()).isEqualTo(PRIMARY_START);
        assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 10));
        verify(transactionRepository).findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, PRIMARY_START, LocalDate.of(2026, 3, 10), CategoryType.EXPENSE);
        verify(transactionRepository).findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, COMPARE_START, LocalDate.of(2026, 2, 10), CategoryType.EXPENSE);
        verify(transactionQueryService).expenseByImportance(
                WALLET_ID, USER_ID, PRIMARY_START, LocalDate.of(2026, 3, 10), Importance.SHOULDNT_HAVE);
        // SpendingProjectionService must NOT be called — projection was supplied by the caller
        org.mockito.Mockito.verify(spendingProjectionService, org.mockito.Mockito.never())
                .getSpendingProjection(any(), any(), any(), any(), any(), any());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private InsightResponseDto getInsights() {
        return insightEngine.getInsights(WALLET_ID, USER_ID, PeriodType.CUSTOM, PRIMARY_START, PRIMARY_END);
    }

    private void stubPrimaryTotals(String income, String expenses) {
        lenient().when(transactionQueryService.totals(WALLET_ID, USER_ID, PRIMARY_START, PRIMARY_END))
                .thenReturn(new PeriodTotals(new BigDecimal(income), new BigDecimal(expenses)));
    }

    private void stubCompareExpenses(String amount) {
        lenient().when(transactionQueryService.sum(
                        WALLET_ID, USER_ID, COMPARE_START, COMPARE_END, CategoryType.EXPENSE))
                .thenReturn(new BigDecimal(amount));
    }

    private InsightEngine engineAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new InsightEngine(
                transactionQueryService, walletService, spendingProjectionService,
                InsightThresholds.defaults(), clock, periodService,
                new com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService(transactionRepository));
    }

    private InsightDto onlyInsightOfType(InsightResponseDto result, InsightType type) {
        assertThat(result.insights()).extracting(InsightDto::type).contains(type);
        return result.insights().stream()
                .filter(i -> i.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private SpendingProjectionDto projection(String safeToSpend) {
        return new SpendingProjectionDto(
                PeriodType.CUSTOM, "Custom range",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                31, 15, 16,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(safeToSpend),
                true, null);
    }

    private CategoryBreakdownProjection category(Integer categoryId, String name, String amount) {
        return new CategoryBreakdownProjection() {
            @Override public Integer getCategoryId()    { return categoryId; }
            @Override public String getName()           { return name; }
            @Override public String getEmoji()          { return null; }
            @Override public BigDecimal getAmount()     { return new BigDecimal(amount); }
            @Override public Long getTransactionCount() { return 1L; }
        };
    }
}
