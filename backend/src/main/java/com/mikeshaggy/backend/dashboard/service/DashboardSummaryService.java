package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregation;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.budget.domain.CategoryBudget;
import com.mikeshaggy.backend.budget.repository.CategoryBudgetRepository;
import com.mikeshaggy.backend.common.calculation.budget.BudgetUsageCalculator;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.insight.InsightDto;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.analytics.insight.InsightResponseDto;
import com.mikeshaggy.backend.analytics.insight.PrecomputedInsightData;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.PeriodTotals;
import com.mikeshaggy.backend.common.calculation.DeltaCalculator;
import com.mikeshaggy.backend.common.paycycle.CycleState;
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
import java.util.Map;
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
    private final CategoryBudgetRepository categoryBudgetRepository;
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
        LocalDate periodEnd = primary.endDate();
        LocalDate cutoffDate = cutoffDate(asOfDate, today, primary);
        boolean fixedPaymentsShown = fixedPaymentsShown(primary.periodType());

        ComparisonWindow comparison = comparisonWindow(compare, primary.startDate(), cutoffDate);

        PeriodTotals currentTotals = transactionQueryService.totals(
                walletId, userId, primary.startDate(), cutoffDate);
        PeriodTotals compareTotals = comparison.available()
                ? transactionQueryService.totals(walletId, userId, comparison.startDate(), comparison.endDate())
                : new PeriodTotals(zero(), zero());

        KpiSnapshot currentSnapshot = snapshot(currentTotals);
        KpiSnapshot compareSnapshot = snapshot(compareTotals);
        DashboardKpisDto kpis = kpis(currentSnapshot, compareSnapshot, comparison.available());

        // The committed window ([start, expectedNextAnchor − 1], through today while awaiting salary) is derived
        // from the resolved period inside the fixed-payment service, shared with /api/fixed-payments/tile.
        FixedTransactionsTileDto fixedPaymentsTile = fixedPaymentsShown
                ? fixedPaymentDashboardService.getFixedPaymentsTileData(primary, wallet, userId, cutoffDate)
                : null;
        SpendingProjectionDto projection = spendingProjectionService.getSpendingProjection(
                wallet, userId, primary, cutoffDate,
                fixedPaymentsTile == null ? null : fixedPaymentsTile.summary().remainingAmount());
        CategoryAggregationResult currentCategories = categoryAggregationService.aggregateExpenses(
                walletId, userId, primary.startDate(), cutoffDate, resolvedCategoryMode);
        CategoryAggregationResult compareCategories = comparison.available()
                ? categoryAggregationService.aggregateExpenses(
                        walletId, userId, comparison.startDate(), comparison.endDate(), resolvedCategoryMode)
                : null;

        Map<Integer, CategoryBudget> budgetByCategory = categoryBudgetRepository
                .findActiveByWalletIdAndUserId(walletId, userId)
                .stream()
                .collect(Collectors.toMap(b -> b.getCategory().getId(), Function.identity()));

        List<DashboardCategoryPressureItemDto> categoryPressure = categoryPressure(
                currentCategories, compareCategories, comparison, currentTotals.expenses(), budgetByCategory);

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
        DashboardCycleHealthDto cycleHealth = cycleHealth(primary, wallet.getBalance(), projection, expensesDelta);
        DashboardPeriodDto period = period(primary, cutoffDate, cutoffDate, periodEnd, comparison);
        DashboardFixedPaymentsDto fixedPayments = fixedPaymentsTile == null ? null : fixedPayments(fixedPaymentsTile);
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
        int daysElapsed = InclusiveDateRange.daysBetween(primary.startDate(), cutoffDate);
        int daysRemaining = Math.max(0, InclusiveDateRange.daysBetween(cutoffDate.plusDays(1), periodEnd));
        // A cycle awaiting its salary is as long as it has been so far; every other period has a known end.
        int daysInPeriod = primary.cycleState() == CycleState.AWAITING_SALARY
                ? daysElapsed
                : InclusiveDateRange.daysBetween(primary.startDate(), periodEnd);
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
                comparison.available() ? comparison.endDate() : null,
                primary.cycleState(),
                primary.expectedNextAnchorDate(),
                primary.salaryWallet(),
                isReportingPeriod(primary.periodType()));
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

    /**
     * A health verdict exists only for the salary wallet's open pay cycle. Reporting periods (MONTHLY, CUSTOM,
     * LAST_PAY_CYCLE), non-salary wallets and legacy-resolved cycles get {@code null}; a cycle awaiting its salary
     * gets a verdict-less shell carrying the current balance and the reason.
     */
    private DashboardCycleHealthDto cycleHealth(PeriodDto primary,
                                                BigDecimal currentBalance,
                                                SpendingProjectionDto projection,
                                                DeltaCalculator.Delta expensesDelta) {
        if (primary.periodType() != PeriodType.PAY_CYCLE || !Boolean.TRUE.equals(primary.salaryWallet())) {
            return null;
        }
        if (primary.cycleState() == CycleState.AWAITING_SALARY) {
            return new DashboardCycleHealthDto(
                    null, money(currentBalance), null, null, null, null, null,
                    false, SpendingProjectionCalculator.REASON_AWAITING_SALARY);
        }
        if (primary.cycleState() != CycleState.OPEN || !projection.projectionAvailable()) {
            return null;
        }
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
                tile.riskIndicator().shortfallAmount(),
                tile.summary().plannedPaidAmount());
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
        PeriodDto primaryWindow = PeriodDto.of(
                primary.startDate(),
                cutoffDate,
                cutoffDate,
                primary.periodType());
        PeriodDto compareWindow = comparison.available()
                ? PeriodDto.of(comparison.startDate(), comparison.endDate(), comparison.endDate(), primary.periodType())
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
            BigDecimal currentExpenses,
            Map<Integer, CategoryBudget> budgetByCategory) {
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
                        comparison.available(),
                        budgetByCategory.get(category.categoryId())))
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
                                                                 boolean comparisonAvailable,
                                                                 CategoryBudget budget) {
        BigDecimal compareAmount = compare == null ? zero() : compare.amount();
        DeltaCalculator.Delta delta = delta(current.amount(), compareAmount, comparisonAvailable);

        BigDecimal budgetAmount = null;
        BigDecimal budgetUsagePercent = null;
        String budgetStatus = null;

        if (budget != null) {
            budgetAmount = budget.getAmount();
            budgetUsagePercent = budget.getAmount().compareTo(BigDecimal.ZERO) == 0
                    ? zero()
                    : money(current.amount()).multiply(HUNDRED).divide(budget.getAmount(), SCALE, ROUNDING);
            budgetStatus = BudgetUsageCalculator
                    .resolveStatus(budgetUsagePercent, budget.getWarningThresholdPercent())
                    .name();
        }

        return new DashboardCategoryPressureItemDto(
                current.categoryId(),
                current.name(),
                current.emoji(),
                current.amount(),
                share(current.amount(), currentExpenses),
                delta.amount(),
                delta.percent(),
                direction(delta.amount()),
                budgetAmount,
                budgetUsagePercent,
                budgetStatus);
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

    /** MONTHLY, CUSTOM and LAST_PAY_CYCLE carry actuals only — no verdict, no projection, no fixed-payments tile. */
    private boolean isReportingPeriod(PeriodType periodType) {
        return periodType != PeriodType.PAY_CYCLE;
    }

    /** The fixed-payments tile is a cycle instrument; the calendar month and custom ranges do not show it (T4/R2). */
    private boolean fixedPaymentsShown(PeriodType periodType) {
        return periodType == PeriodType.PAY_CYCLE || periodType == PeriodType.LAST_PAY_CYCLE;
    }

    /**
     * The data horizon: {@code min(asOfDate, today)} — never in the future — floored at the period start so every
     * "to date" window is well-formed. A period that already ended (a closed cycle, a past month or range) is read
     * up to its own end; only a cycle awaiting its salary keeps counting, and summing, the days after the expected
     * payday — the audited clamp that hid late-salary days is gone.
     */
    private LocalDate cutoffDate(LocalDate asOfDate, LocalDate today, PeriodDto primary) {
        LocalDate cutoff = asOfDate.isAfter(today) ? today : asOfDate;
        if (cutoff.isBefore(primary.startDate())) {
            return primary.startDate();
        }
        boolean horizonIsToday = primary.cycleState() == CycleState.AWAITING_SALARY;
        return !horizonIsToday && cutoff.isAfter(primary.endDate()) ? primary.endDate() : cutoff;
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
