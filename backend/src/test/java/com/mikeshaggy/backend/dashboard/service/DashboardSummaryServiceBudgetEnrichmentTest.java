package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregation;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.analytics.insight.InsightResponseDto;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.PeriodTotals;
import com.mikeshaggy.backend.budget.domain.CategoryBudget;
import com.mikeshaggy.backend.budget.repository.CategoryBudgetRepository;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.dashboard.dto.DashboardCategoryPressureItemDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.RiskIndicatorDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardSummaryServiceBudgetEnrichmentTest {

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
    private CategoryAggregationService categoryAggregationService;

    @Mock
    private CategoryBudgetRepository categoryBudgetRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private DashboardSummaryService dashboardSummaryService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Integer WALLET_ID = 1;
    private static final Integer CATEGORY_ID = 10;

    private static final LocalDate START = LocalDate.of(2026, 5, 1);
    private static final LocalDate END   = LocalDate.of(2026, 5, 31);

    private User user;
    private Wallet wallet;
    private Category groceriesCategory;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email("test@test.com").username("test").build();
        wallet = Wallet.builder().id(WALLET_ID).name("Main").user(user).balance(new BigDecimal("2000.00")).build();
        groceriesCategory = Category.builder()
                .id(CATEGORY_ID).name("Groceries").emoji("🛒").type(CategoryType.EXPENSE).user(user).build();

        when(clock.instant()).thenReturn(Instant.parse("2026-05-20T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID)).thenReturn(wallet);

        PeriodDto primary = new PeriodDto(START, END, LocalDate.of(2026, 6, 1), PeriodType.PAY_CYCLE);
        when(periodService.resolvePeriods(any(), eq(WALLET_ID), eq(USER_ID), any(), any()))
                .thenReturn(new ResolvedPeriods(primary, null));

        PeriodTotals totals = new PeriodTotals(new BigDecimal("3000.00"), new BigDecimal("1200.00"));
        when(transactionQueryService.totals(any(), any(), any(), any())).thenReturn(totals);

        FixedSummaryDto summary = new FixedSummaryDto(
                BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO);
        FixedTransactionsTileDto tile = new FixedTransactionsTileDto(
                START, END, LocalDate.of(2026, 6, 1),
                summary, null,
                new BigDecimal("2000.00"), new BigDecimal("2000.00"),
                new RiskIndicatorDto(false, null),
                List.of(), List.of(), List.of());
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(PeriodDto.class), any(Wallet.class), any(UUID.class), any(LocalDate.class)))
                .thenReturn(tile);

        SpendingProjectionDto projection = new SpendingProjectionDto(
                PeriodType.PAY_CYCLE, "Pay Cycle", START, END,
                31, 20, 11,
                new BigDecimal("3000.00"), new BigDecimal("3000.00"),
                new BigDecimal("1200.00"), new BigDecimal("60.00"),
                new BigDecimal("1860.00"), new BigDecimal("1140.00"),
                BigDecimal.ZERO, new BigDecimal("300.00"), new BigDecimal("150.00"),
                true, null);
        when(spendingProjectionService.getSpendingProjection(
                any(Wallet.class), any(UUID.class), any(PeriodDto.class), any(LocalDate.class), any(BigDecimal.class)))
                .thenReturn(projection);

        CategoryAggregation groceriesAgg = new CategoryAggregation(
                CATEGORY_ID, "Groceries", "🛒", new BigDecimal("600.00"),
                new BigDecimal("50.00"), 5L);
        when(categoryAggregationService.aggregateExpenses(any(), any(), any(), any(), any()))
                .thenReturn(new CategoryAggregationResult(new BigDecimal("1200.00"), List.of(groceriesAgg)));

        when(insightEngine.getInsightsForWindow(any(), any(), any(), any(), any(), any()))
                .thenReturn(new InsightResponseDto(START, END, List.of()));
    }

    @Test
    void budgetExists_pressureItemHasBudgetFields() {
        CategoryBudget budget = CategoryBudget.builder()
                .id(1).wallet(wallet).category(groceriesCategory)
                .amount(new BigDecimal("1000.00")).warningThresholdPercent(80).active(true).build();

        when(categoryBudgetRepository.findActiveByWalletIdAndUserId(WALLET_ID, USER_ID))
                .thenReturn(List.of(budget));

        DashboardSummaryDto result = dashboardSummaryService.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.categoryPressure()).hasSize(1);
        DashboardCategoryPressureItemDto item = result.categoryPressure().get(0);

        assertThat(item.budgetAmount()).isEqualByComparingTo("1000.00");
        // 600 / 1000 * 100 = 60.00%
        assertThat(item.budgetUsagePercent()).isEqualByComparingTo("60.00");
        assertThat(item.budgetStatus()).isEqualTo("OK");
    }

    @Test
    void noBudget_pressureItemBudgetFieldsAreNull() {
        when(categoryBudgetRepository.findActiveByWalletIdAndUserId(WALLET_ID, USER_ID))
                .thenReturn(List.of());

        DashboardSummaryDto result = dashboardSummaryService.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.categoryPressure()).hasSize(1);
        DashboardCategoryPressureItemDto item = result.categoryPressure().get(0);

        assertThat(item.budgetAmount()).isNull();
        assertThat(item.budgetUsagePercent()).isNull();
        assertThat(item.budgetStatus()).isNull();
    }

    @Test
    void spendAtWarningThreshold_budgetStatusIsWarning() {
        // 800 / 1000 * 100 = 80.00% — exactly at threshold
        CategoryAggregation atThresholdAgg = new CategoryAggregation(
                CATEGORY_ID, "Groceries", "🛒", new BigDecimal("800.00"),
                new BigDecimal("66.67"), 8L);
        when(categoryAggregationService.aggregateExpenses(any(), any(), any(), any(), any()))
                .thenReturn(new CategoryAggregationResult(new BigDecimal("1200.00"), List.of(atThresholdAgg)));

        CategoryBudget budget = CategoryBudget.builder()
                .id(1).wallet(wallet).category(groceriesCategory)
                .amount(new BigDecimal("1000.00")).warningThresholdPercent(80).active(true).build();
        when(categoryBudgetRepository.findActiveByWalletIdAndUserId(WALLET_ID, USER_ID))
                .thenReturn(List.of(budget));

        DashboardSummaryDto result = dashboardSummaryService.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        DashboardCategoryPressureItemDto item = result.categoryPressure().get(0);
        assertThat(item.budgetStatus()).isEqualTo("WARNING");
    }

    @Test
    void spendExceedsBudget_budgetStatusIsExceeded() {
        // 1200 / 1000 * 100 = 120%
        CategoryAggregation exceededAgg = new CategoryAggregation(
                CATEGORY_ID, "Groceries", "🛒", new BigDecimal("1200.00"),
                new BigDecimal("100.00"), 12L);
        when(categoryAggregationService.aggregateExpenses(any(), any(), any(), any(), any()))
                .thenReturn(new CategoryAggregationResult(new BigDecimal("1200.00"), List.of(exceededAgg)));

        CategoryBudget budget = CategoryBudget.builder()
                .id(1).wallet(wallet).category(groceriesCategory)
                .amount(new BigDecimal("1000.00")).warningThresholdPercent(80).active(true).build();
        when(categoryBudgetRepository.findActiveByWalletIdAndUserId(WALLET_ID, USER_ID))
                .thenReturn(List.of(budget));

        DashboardSummaryDto result = dashboardSummaryService.getSummary(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        DashboardCategoryPressureItemDto item = result.categoryPressure().get(0);
        assertThat(item.budgetStatus()).isEqualTo("EXCEEDED");
        assertThat(item.budgetUsagePercent()).isEqualByComparingTo("120.00");
    }
}
