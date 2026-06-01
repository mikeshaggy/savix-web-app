package com.mikeshaggy.backend.budget.dto;

import com.mikeshaggy.backend.common.calculation.budget.BudgetUsageCalculator;

import java.math.BigDecimal;

public record BudgetUsageItemDto(
        Integer    budgetId,
        Integer    categoryId,
        String     categoryName,
        String     categoryEmoji,
        BigDecimal budgetAmount,
        BigDecimal spentAmount,
        BigDecimal remainingAmount,
        BigDecimal usagePercent,
        Integer    warningThresholdPercent,
        BudgetUsageCalculator.BudgetStatus status,
        Integer    daysRemaining,
        BigDecimal projectedSpend
) {}
