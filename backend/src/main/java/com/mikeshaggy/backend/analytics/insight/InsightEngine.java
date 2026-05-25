package com.mikeshaggy.backend.analytics.insight;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregation;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.dto.ResolvedPeriods;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsightEngine {

    private final TransactionRepository transactionRepository;
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

        BigDecimal income = money(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, primary.startDate(), primary.endDate(), CategoryType.INCOME));
        BigDecimal expenses = money(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, primary.startDate(), primary.endDate(), CategoryType.EXPENSE));
        BigDecimal saved = money(income.subtract(expenses));
        BigDecimal savingsRate = savingsRate(income, expenses);

        BigDecimal compareExpenses = money(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, compare.startDate(), compare.endDate(), CategoryType.EXPENSE));

        List<InsightDto> insights = new ArrayList<>();
        insights.addAll(categorySpikeInsights(walletId, userId, primary, compare));
        highImpulseSpendingInsight(walletId, userId, primary.startDate(), primary.endDate(), expenses)
                .ifPresent(insights::add);
        spendingPaceInsight(walletId, userId, primary, compare, compareExpenses, today)
                .ifPresent(insights::add);
        lowSavingsRateInsight(income, expenses, savingsRate).ifPresent(insights::add);
        safeToSpendInsight(walletId, userId, primary, today).ifPresent(insights::add);
        goodMonthInsight(income, expenses, saved, savingsRate, compareExpenses).ifPresent(insights::add);

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
                            row.categoryId(), BigDecimal.ZERO.setScale(SCALE, ROUNDING));
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
        BigDecimal percent = current.subtract(baseline)
                .multiply(HUNDRED)
                .divide(baseline, SCALE, ROUNDING);
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

        BigDecimal impulse = money(transactionRepository.sumByWalletUserDateRangeTypeAndImportance(
                walletId, userId, from, to, CategoryType.EXPENSE, Importance.SHOULDNT_HAVE));
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
        int compareDays = (int) (compare.endDate().toEpochDay() - compare.startDate().toEpochDay() + 1);
        if (compareExpenses.compareTo(BigDecimal.ZERO) == 0 || compareDays == 0) {
            return Optional.empty();
        }
        BigDecimal compareDailyRate = compareExpenses.divide(BigDecimal.valueOf(compareDays), SCALE, ROUNDING);

        LocalDate paceEnd = (!today.isBefore(primary.startDate()) && !today.isAfter(primary.endDate()))
                ? today : primary.endDate();
        int elapsedDays = (int) (paceEnd.toEpochDay() - primary.startDate().toEpochDay() + 1);
        if (elapsedDays <= 0) {
            return Optional.empty();
        }

        BigDecimal expensesToDate = money(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, primary.startDate(), paceEnd, CategoryType.EXPENSE));
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

    private Optional<InsightDto> safeToSpendInsight(
            Integer walletId, UUID userId, PeriodDto primary, LocalDate today) {
        if (today.isBefore(primary.startDate()) || today.isAfter(primary.endDate())) {
            return Optional.empty();
        }

        SpendingProjectionDto projection = spendingProjectionService.getSpendingProjection(
                walletId, userId, PeriodType.CUSTOM, primary.startDate(), primary.endDate());
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

    private BigDecimal savingsRate(BigDecimal income, BigDecimal expenses) {
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return income.subtract(expenses).multiply(HUNDRED).divide(income, SCALE, ROUNDING);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
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
