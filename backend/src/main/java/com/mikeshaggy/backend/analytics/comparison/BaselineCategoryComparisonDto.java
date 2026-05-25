package com.mikeshaggy.backend.analytics.comparison;

import java.math.BigDecimal;

public record BaselineCategoryComparisonDto(
        Integer categoryId,
        String name,
        String emoji,
        BigDecimal baselineExpenses,
        BigDecimal currentExpenses,
        BigDecimal expensesDeltaPercent,
        String expensesDeltaDisplay,
        boolean expensesDeltaAvailable
) {
}
