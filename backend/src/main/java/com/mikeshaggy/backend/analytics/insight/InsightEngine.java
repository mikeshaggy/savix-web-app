package com.mikeshaggy.backend.analytics.insight;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregation;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.PeriodTotals;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.calculation.DeltaCalculator;
import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
public class InsightEngine {

    private final AnalyticsTransactionQueryService transactionQueryService;
    private final WalletService walletService;
    private final SpendingProjectionService spendingProjectionService;
    private final InsightThresholds thresholds;
    private final Clock clock;
    private final PeriodService periodService;
    private final CategoryAggregationService categoryAggregationService;

    public InsightResponseDto getInsights(Integer walletId, UUID userId,
                                          PeriodType periodType, LocalDate startDate, LocalDate endDate) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        ResolvedPeriods resolved = periodService.resolvePeriods(periodType, walletId, userId, startDate, endDate);
        PeriodDto primary = resolved.primary();
        PeriodDto compare = resolved.compare();

        LocalDate today = LocalDate.now(clock);
        SpendingProjectionDto projection = projectionForPeriod(walletId, userId, primary, today);
        return buildInsights(walletId, userId, primary, compare, today, projection);
    }

    public InsightResponseDto getInsightsForWindow(Wallet wallet, UUID userId,
                                                   PeriodDto primary, PeriodDto compare,
                                                   LocalDate asOfDate,
                                                   SpendingProjectionDto precomputedProjection) {
        LocalDate effectiveAsOfDate = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        return buildInsights(wallet.getId(), userId, primary, compare, effectiveAsOfDate, precomputedProjection);
    }

    private SpendingProjectionDto projectionForPeriod(Integer walletId, UUID userId,
                                                      PeriodDto period, LocalDate today) {
        if (today.isBefore(period.startDate()) || today.isAfter(period.endDate())) {
            return null;
        }
        return spendingProjectionService.getSpendingProjection(
                walletId, userId, PeriodType.CUSTOM, period.startDate(), period.endDate(), today);
    }

    private InsightResponseDto buildInsights(Integer walletId, UUID userId,
                                             PeriodDto primary, PeriodDto compare,
                                             LocalDate asOfDate,
                                             SpendingProjectionDto projection) {
        PeriodTotals totals = transactionQueryService.totals(walletId, userId, primary.startDate(), primary.endDate());
        BigDecimal income = totals.income();
        BigDecimal expenses = totals.expenses();
        BigDecimal saved = totals.balance();
        BigDecimal savingsRate = fromIncomeAndExpenses(income, expenses);

        BigDecimal compareExpenses = compare == null
                ? zero()
                : transactionQueryService.sum(
                        walletId, userId, compare.startDate(), compare.endDate(), CategoryType.EXPENSE);

        List<InsightDto> insights = new ArrayList<>();
        if (compare != null) {
            insights.addAll(categorySpikeInsights(walletId, userId, primary, compare));
        }
        highImpulseSpendingInsight(walletId, userId, primary.startDate(), primary.endDate(), expenses)
                .ifPresent(insights::add);
        if (compare != null) {
            spendingPaceInsight(walletId, userId, primary, compare, compareExpenses, asOfDate)
                    .ifPresent(insights::add);
        }
        lowSavingsRateInsight(income, expenses, savingsRate).ifPresent(insights::add);
        safeToSpendInsight(projection).ifPresent(insights::add);
        if (compare != null) {
            goodMonthInsight(income, expenses, saved, savingsRate, compareExpenses).ifPresent(insights::add);
        }

        return new InsightResponseDto(
                primary.startDate(),
                primary.endDate(),
                insights.stream().sorted(insightComparator()).toList());
    }

    private List<InsightDto> categorySpikeInsights(
            Integer walletId, UUID userId, PeriodDto primary, PeriodDto compare) {
        CategoryAggregationResult currentRows = categoryAggregationService.aggregateExpenses(
                walletId, userId, primary.startDate(), primary.endDate(),
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);
        CategoryAggregationResult compareRows = categoryAggregationService.aggregateExpenses(
                walletId, userId, compare.startDate(), compare.endDate(),
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        Map<Integer, BigDecimal> compareByCategory = compareRows.categories().stream()
                .collect(Collectors.toMap(
                        CategoryAggregation::categoryId,
                        CategoryAggregation::amount));

        return currentRows.categories().stream()
                .map(row -> {
                    BigDecimal current = row.amount();
                    BigDecimal baseline = compareByCategory.getOrDefault(
                            row.categoryId(), zero());
                    return new CategoryComparison(row, current, baseline);
                })
                .filter(c -> c.baseline().compareTo(BigDecimal.ZERO) > 0)
                .filter(c -> c.current().compareTo(thresholds.minimumCategorySpikeAmount()) >= 0)
                .filter(c -> c.current().compareTo(
                        c.baseline().multiply(thresholds.categorySpikeMultiplier())) > 0)
                .map(c -> categorySpikeInsight(c.row(), c.current(), c.baseline()))
                .toList();
    }

    private InsightDto categorySpikeInsight(CategoryAggregation row,
                                            BigDecimal current, BigDecimal baseline) {
        BigDecimal percent = DeltaCalculator.percent(current, baseline, NULL_ON_ZERO_BASELINE);
        return new InsightDto(
                InsightType.CATEGORY_SPIKE,
                InsightSeverity.WARN,
                "%s up %s%%".formatted(row.name(), wholePercent(percent)),
                "You spent %s PLN on %s, compared to the previous period (%s PLN)."
                        .formatted(moneyText(current), row.name(), moneyText(baseline)),
                row.categoryId(),
                money(current));
    }

    private Optional<InsightDto> highImpulseSpendingInsight(
            Integer walletId, UUID userId,
            LocalDate from, LocalDate to, BigDecimal expenses) {
        if (expenses.compareTo(BigDecimal.ZERO) == 0) {
            return Optional.empty();
        }

        BigDecimal impulse = transactionQueryService.expenseByImportance(
                walletId, userId, from, to, Importance.SHOULDNT_HAVE);
        BigDecimal share = impulse.multiply(HUNDRED).divide(expenses, SCALE, ROUNDING);
        if (share.compareTo(thresholds.impulseShareThresholdPercent()) <= 0) {
            return Optional.empty();
        }

        return Optional.of(new InsightDto(
                InsightType.HIGH_IMPULSE_SPENDING,
                InsightSeverity.WARN,
                "Impulse spending at %s%%".formatted(wholePercent(share)),
                "%s%% of your expenses were marked SHOULDNT_HAVE, totaling %s PLN."
                        .formatted(percentText(share), moneyText(impulse)),
                null,
                impulse));
    }

    private Optional<InsightDto> spendingPaceInsight(
            Integer walletId, UUID userId,
            PeriodDto primary, PeriodDto compare,
            BigDecimal compareExpenses, LocalDate today) {
        int compareDays = InclusiveDateRange.daysBetween(compare.startDate(), compare.endDate());
        if (compareExpenses.compareTo(BigDecimal.ZERO) == 0 || compareDays == 0) {
            return Optional.empty();
        }
        BigDecimal compareDailyRate = compareExpenses.divide(BigDecimal.valueOf(compareDays), SCALE, ROUNDING);

        LocalDate paceEnd = (!today.isBefore(primary.startDate()) && !today.isAfter(primary.endDate()))
                ? today : primary.endDate();
        int elapsedDays = InclusiveDateRange.daysBetween(primary.startDate(), paceEnd);
        if (elapsedDays <= 0) {
            return Optional.empty();
        }

        BigDecimal expensesToDate = transactionQueryService.sum(
                walletId, userId, primary.startDate(), paceEnd, CategoryType.EXPENSE);
        BigDecimal currentDailyBurnRate = expensesToDate.divide(BigDecimal.valueOf(elapsedDays), SCALE, ROUNDING);

        if (compareDailyRate.compareTo(BigDecimal.ZERO) == 0
                || currentDailyBurnRate.compareTo(
                        compareDailyRate.multiply(thresholds.paceMultiplier())) <= 0) {
            return Optional.empty();
        }

        return Optional.of(new InsightDto(
                InsightType.SPENDING_PACE_ABOVE_BASELINE,
                InsightSeverity.WARN,
                "Spending pace above baseline",
                "Your daily spending pace is %s PLN, compared to the previous period's %s PLN per day."
                        .formatted(moneyText(currentDailyBurnRate), moneyText(compareDailyRate)),
                null,
                expensesToDate));
    }

    private Optional<InsightDto> lowSavingsRateInsight(
            BigDecimal income, BigDecimal expenses, BigDecimal savingsRate) {
        if (income.compareTo(BigDecimal.ZERO) <= 0
                || savingsRate.compareTo(thresholds.lowSavingsRateThresholdPercent()) >= 0) {
            return Optional.empty();
        }

        return Optional.of(new InsightDto(
                InsightType.LOW_SAVINGS_RATE,
                InsightSeverity.ALERT,
                "Savings rate below 10%",
                "You saved %s%% of your income this period after %s PLN of expenses."
                        .formatted(percentText(savingsRate), moneyText(expenses)),
                null,
                null));
    }

    private Optional<InsightDto> safeToSpendInsight(SpendingProjectionDto projection) {
        if (projection == null) {
            return Optional.empty();
        }
        BigDecimal safeToSpend = money(projection.safeToSpendToday());
        if (safeToSpend.compareTo(BigDecimal.ZERO) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new InsightDto(
                InsightType.SAFE_TO_SPEND_WARNING,
                InsightSeverity.ALERT,
                "Safe-to-spend is below zero",
                "Your safe-to-spend amount is %s PLN for the rest of this period."
                        .formatted(moneyText(safeToSpend)),
                null,
                safeToSpend));
    }

    private Optional<InsightDto> goodMonthInsight(
            BigDecimal income, BigDecimal expenses,
            BigDecimal saved, BigDecimal savingsRate,
            BigDecimal compareExpenses) {
        if (income.compareTo(BigDecimal.ZERO) <= 0
                || compareExpenses.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        if (expenses.compareTo(compareExpenses.multiply(thresholds.goodMonthExpenseMultiplier())) >= 0
                || savingsRate.compareTo(thresholds.goodMonthSavingsRatePercent()) <= 0) {
            return Optional.empty();
        }

        BigDecimal delta = money(compareExpenses.subtract(expenses));
        return Optional.of(new InsightDto(
                InsightType.GOOD_MONTH,
                InsightSeverity.INFO,
                "Strong savings period",
                "Your expenses are %s PLN below the previous period and your savings rate is %s%%."
                        .formatted(moneyText(delta), percentText(savingsRate)),
                null,
                saved));
    }

    private String moneyText(BigDecimal value) {
        return money(value).stripTrailingZeros().toPlainString();
    }

    private String percentText(BigDecimal value) {
        return value.setScale(1, ROUNDING).stripTrailingZeros().toPlainString();
    }

    private String wholePercent(BigDecimal value) {
        return value.setScale(0, ROUNDING).toPlainString();
    }

    private Comparator<InsightDto> insightComparator() {
        return Comparator
                .comparingInt((InsightDto insight) -> severityRank(insight.severity()))
                .thenComparing(this::amountImportance, Comparator.reverseOrder());
    }

    private BigDecimal amountImportance(InsightDto insight) {
        return insight.relatedAmount() == null ? BigDecimal.ZERO : insight.relatedAmount().abs();
    }

    private int severityRank(InsightSeverity severity) {
        return switch (severity) {
            case ALERT -> 0;
            case WARN -> 1;
            case INFO -> 2;
        };
    }

    private record CategoryComparison(
            CategoryAggregation row,
            BigDecimal current,
            BigDecimal baseline) {
    }
}
