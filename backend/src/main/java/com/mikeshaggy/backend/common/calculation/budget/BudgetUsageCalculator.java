package com.mikeshaggy.backend.common.calculation.budget;

import com.mikeshaggy.backend.common.period.InclusiveDateRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

public final class BudgetUsageCalculator {

    private BudgetUsageCalculator() {
    }

    public static List<BudgetItemResult> calculateItems(
            List<BudgetInput> budgets,
            Map<Integer, BigDecimal> spendByCategory,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate today) {

        int daysInPeriod = InclusiveDateRange.daysBetween(periodStart, periodEnd);
        LocalDate clampedToday = clamp(today, periodStart, periodEnd);
        int daysElapsed  = InclusiveDateRange.daysBetween(periodStart, clampedToday);
        int daysRemaining = Math.max(
                0, InclusiveDateRange.daysBetween(clampedToday.plusDays(1), periodEnd));

        return budgets.stream()
                .map(b -> calculateItem(b, spendByCategory, daysInPeriod, daysElapsed, daysRemaining))
                .toList();
    }

    public static BudgetStatus resolveStatus(BigDecimal usagePercent, int warningThresholdPercent) {
        if (usagePercent.compareTo(HUNDRED) >= 0) {
            return BudgetStatus.EXCEEDED;
        }
        if (usagePercent.compareTo(BigDecimal.valueOf(warningThresholdPercent)) >= 0) {
            return BudgetStatus.WARNING;
        }
        return BudgetStatus.OK;
    }

    private static BudgetItemResult calculateItem(
            BudgetInput budget,
            Map<Integer, BigDecimal> spendByCategory,
            int daysInPeriod,
            int daysElapsed,
            int daysRemaining) {

        BigDecimal spent = spendByCategory.getOrDefault(budget.categoryId(), BigDecimal.ZERO);
        BigDecimal budgetAmount = budget.budgetAmount();

        BigDecimal remaining     = budgetAmount.subtract(spent).setScale(SCALE, ROUNDING);
        BigDecimal usagePercent  = budgetAmount.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(SCALE, ROUNDING)
                : spent.multiply(HUNDRED).divide(budgetAmount, SCALE, ROUNDING);
        BudgetStatus status      = resolveStatus(usagePercent, budget.warningThresholdPercent());
        BigDecimal projected     = projectedSpend(spent, daysElapsed, daysInPeriod);

        return new BudgetItemResult(
                budget.budgetId(),
                budget.categoryId(),
                budget.categoryName(),
                budget.categoryEmoji(),
                budgetAmount,
                spent.setScale(SCALE, ROUNDING),
                remaining,
                usagePercent,
                budget.warningThresholdPercent(),
                status,
                daysRemaining,
                projected);
    }

    private static BigDecimal projectedSpend(BigDecimal spent, int daysElapsed, int daysInPeriod) {
        if (daysElapsed < 1 || daysInPeriod == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return spent
                .multiply(BigDecimal.valueOf(daysInPeriod))
                .divide(BigDecimal.valueOf(daysElapsed), SCALE, ROUNDING);
    }

    private static LocalDate clamp(LocalDate value, LocalDate min, LocalDate max) {
        if (value.isBefore(min)) return min;
        if (value.isAfter(max))  return max;
        return value;
    }

    public enum BudgetStatus {
        OK,
        WARNING,
        EXCEEDED
    }

    public record BudgetInput(
            Integer budgetId,
            Integer categoryId,
            String  categoryName,
            String  categoryEmoji,
            BigDecimal budgetAmount,
            int        warningThresholdPercent
    ) {
    }

    public record BudgetItemResult(
            Integer    budgetId,
            Integer    categoryId,
            String     categoryName,
            String     categoryEmoji,
            BigDecimal budgetAmount,
            BigDecimal spentAmount,
            BigDecimal remainingAmount,
            BigDecimal usagePercent,
            int        warningThresholdPercent,
            BudgetStatus status,
            int        daysRemaining,
            BigDecimal projectedSpend
    ) {
    }
}
