package com.mikeshaggy.backend.analytics;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonResponseDto;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.overview.AnalyticsSummaryDto;
import com.mikeshaggy.backend.analytics.overview.AnalyticsSummaryService;
import com.mikeshaggy.backend.analytics.overview.OverviewStatusCalculator;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.repository.CategoryRepository;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.DailyCategorySpendProjection;
import com.mikeshaggy.backend.transaction.repository.HeatmapProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsRegressionSafetyTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Integer WALLET_ID = 1;
    private static final LocalDate CURRENT_START = LocalDate.of(2026, 3, 1);
    private static final LocalDate CURRENT_END = LocalDate.of(2026, 3, 31);
    private static final LocalDate COMPARE_START = LocalDate.of(2026, 2, 1);
    private static final LocalDate COMPARE_END = LocalDate.of(2026, 2, 28);
    private static final BigDecimal CURRENT_INCOME = new BigDecimal("5000.00");
    private static final BigDecimal CURRENT_EXPENSES = new BigDecimal("1200.00");
    private static final BigDecimal COMPARE_EXPENSES = new BigDecimal("1000.00");

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletService walletService;
    @Mock private PeriodService periodService;
    @Mock private SpendingProjectionService spendingProjectionService;
    @Mock private CategoryAggregationService categoryAggregationService;
    @Mock private CategoryRepository categoryRepository;

    private Wallet wallet;
    private Category incomeCategory;
    private Category expenseCategory;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(USER_ID).email("regression@example.com").username("regression").build();
        wallet = Wallet.builder().id(WALLET_ID).name("Main").balance(new BigDecimal("3800.00")).user(user).build();
        incomeCategory = Category.builder().id(1).name("Salary").type(CategoryType.INCOME).user(user).build();
        expenseCategory = Category.builder().id(2).name("Groceries").type(CategoryType.EXPENSE).user(user).build();

        lenient().when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID)).thenReturn(wallet);
    }

    @Test
    void sharedFixtureKeepsAnalyticsSummaryAndCycleComparisonTotalsAligned() {
        AnalyticsSummaryDto analyticsSummary = analyticsSummary();
        CycleComparisonResponseDto cycleComparison = cycleComparison();

        assertThat(analyticsSummary.incomeForPeriod()).isEqualByComparingTo(CURRENT_INCOME);
        assertThat(analyticsSummary.projectedTotalSpend()).isEqualByComparingTo(CURRENT_EXPENSES);
        assertThat(analyticsSummary.savingsRate()).isEqualByComparingTo("76.00");

        assertThat(cycleComparison.summary().currentExpenses()).isEqualByComparingTo(CURRENT_EXPENSES);
        assertThat(cycleComparison.summary().baselineAverageExpenses()).isEqualByComparingTo(COMPARE_EXPENSES);
    }

    private AnalyticsSummaryDto analyticsSummary() {
        AnalyticsSummaryService service = new AnalyticsSummaryService(
                spendingProjectionService,
                periodService,
                new AnalyticsTransactionQueryService(transactionRepository),
                walletService,
                categoryAggregationService,
                new OverviewStatusCalculator());
        when(spendingProjectionService.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.CUSTOM, CURRENT_START, CURRENT_END))
                .thenReturn(new SpendingProjectionDto(
                        PeriodType.CUSTOM,
                        "Custom range",
                        CURRENT_START,
                        CURRENT_END,
                        31,
                        31,
                        0,
                        CURRENT_INCOME,
                        CURRENT_INCOME,
                        CURRENT_EXPENSES,
                        new BigDecimal("38.71"),
                        CURRENT_EXPENSES,
                        new BigDecimal("3800.00"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        false,
                        "Historical period"));
        when(categoryAggregationService.aggregateExpenses(
                WALLET_ID, USER_ID, CURRENT_START, CURRENT_END, CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));
        when(transactionRepository.findHeatmapByWalletDateRangeAndType(
                WALLET_ID, USER_ID, CURRENT_START, CURRENT_END, CategoryType.EXPENSE))
                .thenReturn(List.of(heatmapRow(LocalDate.of(2026, 3, 31), CURRENT_EXPENSES)));
        when(periodService.resolvePeriods(PeriodType.CUSTOM, WALLET_ID, USER_ID, CURRENT_START, CURRENT_END))
                .thenReturn(new ResolvedPeriods(
                        new PeriodDto(CURRENT_START, CURRENT_END, CURRENT_END, PeriodType.CUSTOM),
                        new PeriodDto(COMPARE_START, COMPARE_END, COMPARE_END, PeriodType.CUSTOM)));
        when(transactionRepository.sumByWalletUserDateRangeAndType(
                WALLET_ID, USER_ID, COMPARE_START, COMPARE_END, CategoryType.EXPENSE))
                .thenReturn(COMPARE_EXPENSES);

        return service.getSummary(WALLET_ID, USER_ID, PeriodType.CUSTOM, CURRENT_START, CURRENT_END);
    }

    private CycleComparisonResponseDto cycleComparison() {
        CycleComparisonService service = new CycleComparisonService(
                transactionRepository,
                walletService,
                categoryRepository,
                Clock.fixed(CURRENT_END.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
        when(categoryRepository.findByUserIdAndIsCycleAnchorTrue(USER_ID)).thenReturn(Optional.empty());
        when(transactionRepository.findDailyCategorySpendByWalletUserDateRangeAndType(
                eq(WALLET_ID),
                eq(USER_ID),
                eq(COMPARE_START),
                eq(CURRENT_END),
                eq(CategoryType.EXPENSE),
                anyList(),
                anyBoolean(),
                anyBoolean(),
                anyList(),
                anyBoolean()))
                .thenReturn(List.of(
                        dailyRow(LocalDate.of(2026, 3, 10), new BigDecimal("800.00")),
                        dailyRow(LocalDate.of(2026, 3, 31), new BigDecimal("400.00")),
                        dailyRow(LocalDate.of(2026, 2, 28), COMPARE_EXPENSES)));

        return service.getCycleComparison(
                WALLET_ID, USER_ID, CURRENT_END, 1, null, CategoryAggregationMode.ALL, null);
    }

    private Transaction transaction(Long id, BigDecimal amount, Category category, LocalDate date) {
        return Transaction.builder()
                .id(id)
                .wallet(wallet)
                .category(category)
                .amount(amount)
                .title(category.getName())
                .transactionDate(date)
                .importance(category.getType() == CategoryType.EXPENSE ? Importance.ESSENTIAL : null)
                .build();
    }

    private HeatmapProjection heatmapRow(LocalDate date, BigDecimal amount) {
        return new HeatmapProjection() {
            @Override public LocalDate getDate() { return date; }
            @Override public Integer getCategoryId() { return expenseCategory.getId(); }
            @Override public String getCategoryName() { return expenseCategory.getName(); }
            @Override public String getEmoji() { return expenseCategory.getEmoji(); }
            @Override public BigDecimal getAmount() { return amount; }
            @Override public Long getTransactions() { return 2L; }
        };
    }

    private DailyCategorySpendProjection dailyRow(LocalDate date, BigDecimal amount) {
        return new DailyCategorySpendProjection() {
            @Override public LocalDate getDate() { return date; }
            @Override public Integer getCategoryId() { return expenseCategory.getId(); }
            @Override public String getName() { return expenseCategory.getName(); }
            @Override public String getEmoji() { return expenseCategory.getEmoji(); }
            @Override public BigDecimal getAmount() { return amount; }
            @Override public Long getTransactionCount() { return 1L; }
        };
    }
}
