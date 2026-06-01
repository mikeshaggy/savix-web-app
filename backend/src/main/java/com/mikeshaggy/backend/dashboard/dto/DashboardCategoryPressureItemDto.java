package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;

public record DashboardCategoryPressureItemDto(
        Integer categoryId,
        String categoryName,
        String categoryEmoji,
        BigDecimal amount,
        BigDecimal shareOfExpensesPercent,
        BigDecimal deltaAmount,
        BigDecimal deltaPercent,
        DashboardCategoryDirection direction,
        // Budget enrichment — null when no active budget exists for this category
        BigDecimal budgetAmount,
        BigDecimal budgetUsagePercent,
        String budgetStatus
) {
}
