package com.mikeshaggy.backend.analytics.overview;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregation;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyExpenseStats;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.calculation.DeltaCalculator;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.DeltaCalculator.ZeroBaselineMode.NULL_ON_ZERO_BASELINE;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.common.calculation.SavingsRateCalculator.fromIncomeAndExpenses;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsSummaryService {

    private final SpendingProjectionService spendingProjectionService;
    private final PeriodService periodService;
    private final AnalyticsTransactionQueryService transactionQueryService;
    private final WalletService walletService;
    private final CategoryAggregationService categoryAggregationService;
    private final OverviewStatusCalculator statusCalculator;

    public AnalyticsSummaryDto getSummary(Integer walletId, UUID userId,
                                          PeriodType periodType,
                                          LocalDate startDate, LocalDate endDate) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        // ── 1. Projection data (covers forecast KPIs + period window) ─────────
        SpendingProjectionDto proj = spendingProjectionService.getSpendingProjection(
                walletId, userId, periodType, startDate, endDate);

        // ── 2. Top spending category ──────────────────────────────────────────
        CategoryAggregationResult categoryResult = categoryAggregationService.aggregateExpenses(
                walletId, userId, proj.startDate(), proj.endDate(),
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);
        CategoryAggregation topCat = categoryResult.categories().isEmpty() ? null : categoryResult.categories().getFirst();

        // ── 3. Daily stats from heatmap rows ──────────────────────────────────
        DailyExpenseStats dailyStats = transactionQueryService.dailyExpenseStats(
                walletId, userId, proj.startDate(), proj.endDate());

        // ── 4. Comparison (vs previous equivalent period) ─────────────────────
        ResolvedPeriods resolvedPeriods = periodService.resolvePeriods(
                periodType, walletId, userId, startDate, endDate);
        PeriodDto compare = resolvedPeriods.compare();

        BigDecimal compareExpenses = compare == null
                ? money(BigDecimal.ZERO)
                : transactionQueryService.sum(
                        walletId, userId, compare.startDate(), compare.endDate(), CategoryType.EXPENSE);
        boolean comparisonAvailable = compareExpenses.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal expensesDeltaPercent = null;
        if (comparisonAvailable) {
            expensesDeltaPercent = DeltaCalculator.percent(
                    proj.expensesToDate(), compareExpenses, NULL_ON_ZERO_BASELINE);
        }

        // ── 5. Savings rate (projected) ───────────────────────────────────────
        BigDecimal savingsRate = null;
        if (proj.incomeForPeriod().compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = fromIncomeAndExpenses(proj.incomeForPeriod(), proj.projectedPeriodExpenses());
        }

        // ── 6. Status ─────────────────────────────────────────────────────────
        OverviewStatus status = statusCalculator.compute(proj);

        return new AnalyticsSummaryDto(
                status,
                proj.startDate(),
                proj.endDate(),
                proj.daysInPeriod(),
                proj.daysElapsed(),
                proj.daysRemaining(),
                proj.projectionAvailable(),
                proj.projectedEndBalance(),
                proj.safeToSpendToday(),
                proj.safeToSpendPerDay(),
                proj.dailyBurnRate(),
                savingsRate,
                proj.projectedPeriodExpenses(),
                proj.incomeForPeriod(),
                topCat != null ? topCat.categoryId() : null,
                topCat != null ? topCat.name() : null,
                topCat != null ? topCat.emoji() : null,
                topCat != null ? topCat.amount() : null,
                dailyStats.highestSpendingDay(),
                dailyStats.highestSpendingDayAmount(),
                dailyStats.activeDays(),
                expensesDeltaPercent,
                comparisonAvailable);
    }

}
