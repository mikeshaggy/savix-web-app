package com.mikeshaggy.backend.analytics.cyclecomparison;

import java.math.BigDecimal;

public record CycleComparisonCategoryDto(
        Integer categoryId,
        String name,
        String emoji,
        BigDecimal currentAmount,
        BigDecimal baselineAverageAmount,
        BigDecimal deltaAmount,
        BigDecimal deltaPercent,
        long currentTransactionCount,
        BigDecimal baselineAverageTransactionCount,
        CycleComparisonStatus status
) {
}
