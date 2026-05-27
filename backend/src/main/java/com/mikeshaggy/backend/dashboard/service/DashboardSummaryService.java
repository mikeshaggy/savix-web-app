package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregation;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.insight.InsightDto;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.analytics.insight.InsightResponseDto;
import com.mikeshaggy.backend.analytics.insight.PrecomputedInsightData;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.PeriodTotals;
import com.mikeshaggy.backend.common.calculation.DeltaCalculator;
import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.dashboard.dto.DashboardCategoryDirection;
import com.mikeshaggy.backend.dashboard.dto.DashboardCategoryPressureItemDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardCycleHealthDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardFixedPaymentOccurrenceDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardFixedPaymentsDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardHealthStatus;
import com.mikeshaggy.backend.dashboard.dto.DashboardInsightDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardKpisDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardMoneyKpiDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardPercentKpiDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardPeriodDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardPreviousCyclePreviewDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;
import static com.mikeshaggy.backend.common.calculation.DeltaCalculator.ZeroBaselineMode.NULL_ON_ZERO_BASELINE;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.zero;
import static com.mikeshaggy.backend.common.calculation.SavingsRateCalculator.fromIncomeAndExpenses;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardSummaryService {

    private static final BigDecimal WARNING_SPENDING_PACE_THRESHOLD = new BigDecimal("20.00");
    private static final int DASHBOARD_INSIGHT_LIMIT = 5;
    private static final int DASHBOARD_CATEGORY_LIMIT = 6;
    private static final int UPCOMING_FIXED_PAYMENT_LIMIT = 3;

    private final PeriodService periodService;
    private final WalletService walletService;
    private final AnalyticsTransactionQueryService transactionQueryService;
    private final SpendingProjectionService spendingProjectionService;
    private final FixedPaymentDashboardService fixedPaymentDashboardService;
    private final InsightEngine insightEngine;
    private final CategoryAggregationService categoryAggregationService;
    private final Clock clock;

    public DashboardSummaryDto getSummary(Integer walletId, UUID userId,
                                          PeriodType periodType,
                                          LocalDate startDate, LocalDate endDate,
                                          LocalDate requestedAsOfDate,
                                          CategoryAggregationMode categoryMode) {
        PeriodType resolvedPeriodType = periodType == null ? PeriodType.PAY_CYCLE : periodType;
        CategoryAggregationMode resolvedCategoryMode = categoryMode == null
                ? CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES
                : categoryMode;

        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        ResolvedPeriods periods = periodService.resolvePeriods(
                resolvedPeriodType, walletId, userId, startDate, endDate);
        PeriodDto primary = periods.primary();
        PeriodDto compare = periods.compare();

        LocalDate today = LocalDate.now(clock);
        LocalDate asOfDate = requestedAsOfDate == null ? today : requestedAsOfDate;
        LocalDate periodEnd = dashboardPeriodEnd(primary);
        LocalDate cutoffDate = clamp(asOfDate, primary.startDate(), periodEnd);
        PeriodDto fixedPaymentPeriod = new PeriodDto(
                primary.startDate(), periodEnd, periodEnd, primary.periodType());

        ComparisonWindow comparison = comparisonWindow(compare, primary.startDate(), cutoffDate);

        PeriodTotals currentTotals = transactionQueryService.totals(
                walletId, userId, primary.startDate(), cutoffDate);
        PeriodTotals compareTotals = comparison.available()
                ? transactionQueryService.totals(walletId, userId, comparison.startDate(), comparison.endDate())
                : new PeriodTotals(zero(), zero());

        KpiSnapshot currentSnapshot = snapshot(currentTotals);
        KpiSnapshot compareSnapshot = snapshot(compareTotals);
        DashboardKpisDto kpis = kpis(currentSnapshot, compareSnapshot, comparison.available());

        FixedTransactionsTileDto fixedPaymentsTile = fixedPaymentDashboardService
                .getFixedPaymentsTileData(fixedPaymentPeriod, wallet, userId, cutoffDate);
        SpendingProjectionDto projection = spendingProjectionService.getSpendingProjection(
                wallet, userId, primary, cutoffDate, fixedPaymentsTile.summary().remainingAmount());
        CategoryAggregationResult currentCategories = categoryAggregationService.aggregateExpenses(
                walletId, userId, primary.startDate(), cutoffDate, resolvedCategoryMode);
        CategoryAggregationResult compareCategories = comparison.available()
                ? categoryAggregationService.aggregateExpenses(
                        walletId, userId, comparison.startDate(), comparison.endDate(), resolvedCategoryMode)
                : null;

        List<DashboardCategoryPressureItemDto> categoryPressure = categoryPressure(
                currentCategories, compareCategories, comparison, currentTotals.expenses());

        // Share precomputed categories with InsightEngine only when the mode matches what it uses internally
        CategoryAggregationResult insightCurrentCats = resolvedCategoryMode == CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES
                ? currentCategories : null;
        CategoryAggregationResult insightCompareCats = resolvedCategoryMode == CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES
                ? compareCategories : null;
        PrecomputedInsightData insightData = new PrecomputedInsightData(
                projection, currentTotals, compareTotals.expenses(), insightCurrentCats, insightCompareCats);
        List<DashboardInsightDto> insights = insights(wallet, userId, primary, comparison, cutoffDate, insightData);

        DeltaCalculator.Delta expensesDelta = delta(
                currentSnapshot.expenses(), compareSnapshot.expenses(), comparison.available());
        DashboardCycleHealthDto cycleHealth = cycleHealth(wallet.getBalance(), projection, expensesDelta);
        DashboardPeriodDto period = period(primary, cutoffDate, cutoffDate, periodEnd, comparison);
        DashboardFixedPaymentsDto fixedPayments = fixedPayments(fixedPaymentsTile);
        DashboardPreviousCyclePreviewDto previousCyclePreview = previousCyclePreview(
                kpis, categoryPressure, comparison.available());

        return new DashboardSummaryDto(
                walletId,
                wallet.getName(),
                period,
                cycleHealth,
                kpis,
                fixedPayments,
                insights,
                categoryPressure,
                previousCyclePreview);
    }

    private DashboardPeriodDto period(PeriodDto primary, LocalDate asOfDate, LocalDate cutoffDate,
                                      LocalDate periodEnd, ComparisonWindow comparison) {
        int daysInPeriod = InclusiveDateRange.daysBetween(primary.startDate(), periodEnd);
        int daysElapsed = InclusiveDateRange.daysBetween(primary.startDate(), cutoffDate);
        int daysRemaining = Math.max(0, InclusiveDateRange.daysBetween(cutoffDate.plusDays(1), periodEnd));
        return new DashboardPeriodDto(
                primary.periodType(),
                primary.startDate(),
                periodEnd,
                primary.billingEndDate(),
                asOfDate,
                cutoffDate,
                daysInPeriod,
                daysElapsed,
                daysRemaining,
                comparison.available(),
                comparison.available() ? comparison.startDate() : null,
                comparison.available() ? comparison.endDate() : null);
    }

    private DashboardKpisDto kpis(KpiSnapshot current, KpiSnapshot compare, boolean comparisonAvailable) {
        return new DashboardKpisDto(
                moneyKpi(current.income(), compare.income(), comparisonAvailable),
                moneyKpi(current.expenses(), compare.expenses(), comparisonAvailable),
                moneyKpi(current.saved(), compare.saved(), comparisonAvailable),
                new DashboardPercentKpiDto(
                        current.savingsRate(),
                        comparisonAvailable ? money(current.savingsRate().subtract(compare.savingsRate())) : null));
    }

    private DashboardMoneyKpiDto moneyKpi(BigDecimal current, BigDecimal compare, boolean comparisonAvailable) {
        DeltaCalculator.Delta delta = delta(current, compare, comparisonAvailable);
        return new DashboardMoneyKpiDto(
                money(current),
                delta.amount(),
                delta.percent());
    }

    private DeltaCalculator.Delta delta(BigDecimal current, BigDecimal compare, boolean comparisonAvailable) {
        if (!comparisonAvailable) {
            return new DeltaCalculator.Delta(null, null, false);
        }
        return DeltaCalculator.amountAndPercent(money(current), money(compare), NULL_ON_ZERO_BASELINE);
    }

    private DashboardCycleHealthDto cycleHealth(BigDecimal currentBalance,
                                                SpendingProjectionDto projection,
                                                DeltaCalculator.Delta expensesDelta) {
        DashboardHealthStatus status = healthStatus(projection, expensesDelta.percent());
        return new DashboardCycleHealthDto(
                status,
                money(currentBalance),
                projection.safeToSpendToday(),
                projection.safeToSpendPerDay(),
                projection.projectedEndBalance(),
                expensesDelta.amount(),
                expensesDelta.percent(),
                projection.projectionAvailable(),
                projection.projectionReason());
    }

    private DashboardHealthStatus healthStatus(SpendingProjectionDto projection, BigDecimal spendingPaceDeltaPercent) {
        if (projection.safeToSpendToday().compareTo(BigDecimal.ZERO) < 0
                || projection.projectedEndBalance().compareTo(BigDecimal.ZERO) < 0) {
            return DashboardHealthStatus.DANGER;
        }
        if (spendingPaceDeltaPercent != null
                && spendingPaceDeltaPercent.compareTo(WARNING_SPENDING_PACE_THRESHOLD) > 0) {
            return DashboardHealthStatus.WARNING;
        }
        return DashboardHealthStatus.ON_TRACK;
    }

    private DashboardFixedPaymentsDto fixedPayments(FixedTransactionsTileDto tile) {
        List<DashboardFixedPaymentOccurrenceDto> upcoming = tile.upcoming().stream()
                .limit(UPCOMING_FIXED_PAYMENT_LIMIT)
                .map(this::fixedPaymentOccurrence)
                .toList();
        return new DashboardFixedPaymentsDto(
                tile.summary().plannedAmount(),
                tile.summary().paidAmount(),
                tile.summary().remainingAmount(),
                tile.summary().overdueAmount(),
                tile.summary().paidCount(),
                tile.summary().plannedCount(),
                upcoming.isEmpty() ? null : upcoming.getFirst(),
                upcoming,
                tile.balanceAfterFixed(),
                tile.riskIndicator().isAtRisk(),
                tile.riskIndicator().shortfallAmount());
    }

    private DashboardFixedPaymentOccurrenceDto fixedPaymentOccurrence(FixedOccurrenceRowDto row) {
        return new DashboardFixedPaymentOccurrenceDto(
                row.occurrenceId(),
                row.title(),
                row.expectedAmount(),
                row.dueDate(),
                row.status(),
                row.categoryName(),
                row.categoryId());
    }

    private List<DashboardInsightDto> insights(Wallet wallet, UUID userId, PeriodDto primary,
                                               ComparisonWindow comparison, LocalDate cutoffDate,
                                               PrecomputedInsightData precomputed) {
        PeriodDto primaryWindow = new PeriodDto(
                primary.startDate(),
                cutoffDate,
                cutoffDate,
                primary.periodType());
        PeriodDto compareWindow = comparison.available()
                ? new PeriodDto(comparison.startDate(), comparison.endDate(), comparison.endDate(), primary.periodType())
                : null;
        InsightResponseDto response = insightEngine.getInsightsForWindow(
                wallet, userId, primaryWindow, compareWindow, cutoffDate, precomputed);
        return response.insights().stream()
                .limit(DASHBOARD_INSIGHT_LIMIT)
                .map(this::insight)
                .toList();
    }

    private DashboardInsightDto insight(InsightDto insight) {
        return new DashboardInsightDto(
                insight.type(),
                insight.severity(),
                insight.title(),
                insight.description(),
                insight.relatedAmount());
    }

    private List<DashboardCategoryPressureItemDto> categoryPressure(
            CategoryAggregationResult current,
            CategoryAggregationResult compare,
            ComparisonWindow comparison,
            BigDecimal currentExpenses) {
        var compareByCategory = comparison.available() && compare != null
                ? compare.categories()
                        .stream()
                        .collect(Collectors.toMap(CategoryAggregation::categoryId, Function.identity()))
                : java.util.Map.<Integer, CategoryAggregation>of();

        return current.categories().stream()
                .map(category -> categoryPressureItem(
                        category,
                        compareByCategory.get(category.categoryId()),
                        currentExpenses,
                        comparison.available()))
                .sorted(Comparator
                        .comparing((DashboardCategoryPressureItemDto item) -> positiveDelta(item.deltaAmount()))
                        .reversed()
                        .thenComparing(DashboardCategoryPressureItemDto::amount, Comparator.reverseOrder())
                        .thenComparing(DashboardCategoryPressureItemDto::categoryName))
                .limit(DASHBOARD_CATEGORY_LIMIT)
                .toList();
    }

    private DashboardCategoryPressureItemDto categoryPressureItem(CategoryAggregation current,
                                                                 CategoryAggregation compare,
                                                                 BigDecimal currentExpenses,
                                                                 boolean comparisonAvailable) {
        BigDecimal compareAmount = compare == null ? zero() : compare.amount();
        DeltaCalculator.Delta delta = delta(current.amount(), compareAmount, comparisonAvailable);
        return new DashboardCategoryPressureItemDto(
                current.categoryId(),
                current.name(),
                current.emoji(),
                current.amount(),
                share(current.amount(), currentExpenses),
                delta.amount(),
                delta.percent(),
                direction(delta.amount()));
    }

    private DashboardPreviousCyclePreviewDto previousCyclePreview(DashboardKpisDto kpis,
                                                                  List<DashboardCategoryPressureItemDto> categoryPressure,
                                                                  boolean comparisonAvailable) {
        return new DashboardPreviousCyclePreviewDto(
                comparisonAvailable ? kpis.expenses().deltaAmount() : null,
                comparisonAvailable ? kpis.expenses().deltaPercent() : null,
                comparisonAvailable ? kpis.income().deltaAmount() : null,
                comparisonAvailable ? kpis.income().deltaPercent() : null,
                comparisonAvailable ? kpis.saved().deltaAmount() : null,
                comparisonAvailable ? kpis.saved().deltaPercent() : null,
                comparisonAvailable ? kpis.savingsRate().deltaPercentagePoints() : null,
                categoryPressure.stream()
                        .filter(item -> item.deltaAmount() != null)
                        .limit(3)
                        .toList());
    }

    private KpiSnapshot snapshot(PeriodTotals totals) {
        BigDecimal income = money(totals.income());
        BigDecimal expenses = money(totals.expenses());
        BigDecimal saved = money(income.subtract(expenses));
        BigDecimal savingsRate = fromIncomeAndExpenses(income, expenses);
        return new KpiSnapshot(income, expenses, saved, savingsRate);
    }

    private LocalDate dashboardPeriodEnd(PeriodDto period) {
        return period.periodType() == PeriodType.PAY_CYCLE
                ? period.billingEndDate().minusDays(1)
                : period.endDate();
    }

    private ComparisonWindow comparisonWindow(PeriodDto compare, LocalDate currentStart, LocalDate cutoffDate) {
        if (compare == null) {
            return ComparisonWindow.unavailable();
        }
        long dayOffset = java.time.temporal.ChronoUnit.DAYS.between(currentStart, cutoffDate);
        LocalDate compareCutoff = compare.startDate().plusDays(dayOffset);
        LocalDate compareEnd = compareCutoff.isAfter(compare.endDate()) ? compare.endDate() : compareCutoff;
        if (compareEnd.isBefore(compare.startDate())) {
            return ComparisonWindow.unavailable();
        }
        return new ComparisonWindow(true, compare.startDate(), compareEnd);
    }

    private LocalDate clamp(LocalDate value, LocalDate min, LocalDate max) {
        if (value.isBefore(min)) {
            return min;
        }
        if (value.isAfter(max)) {
            return max;
        }
        return value;
    }

    private BigDecimal share(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return zero();
        }
        return money(amount).multiply(HUNDRED).divide(total, SCALE, ROUNDING);
    }

    private BigDecimal positiveDelta(BigDecimal deltaAmount) {
        if (deltaAmount == null || deltaAmount.compareTo(BigDecimal.ZERO) < 0) {
            return zero();
        }
        return deltaAmount;
    }

    private DashboardCategoryDirection direction(BigDecimal deltaAmount) {
        if (deltaAmount == null || deltaAmount.compareTo(BigDecimal.ZERO) == 0) {
            return DashboardCategoryDirection.FLAT;
        }
        return deltaAmount.compareTo(BigDecimal.ZERO) > 0
                ? DashboardCategoryDirection.UP
                : DashboardCategoryDirection.DOWN;
    }

    private record KpiSnapshot(
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal saved,
            BigDecimal savingsRate) {
    }

    private record ComparisonWindow(boolean available, LocalDate startDate, LocalDate endDate) {
        static ComparisonWindow unavailable() {
            return new ComparisonWindow(false, null, null);
        }
    }
}
