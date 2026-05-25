package com.mikeshaggy.backend.analytics.comparison;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BaselineComparisonDto(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate compareStart,
        LocalDate compareEnd,
        BigDecimal baselineIncome,
        BigDecimal baselineExpenses,
        BigDecimal baselineSavingsRate,
        BigDecimal currentIncome,
        BigDecimal currentExpenses,
        BigDecimal currentSavingsRate,
        BigDecimal incomeDeltaPercent,
        String incomeDeltaDisplay,
        boolean incomeDeltaAvailable,
        BigDecimal expensesDeltaPercent,
        String expensesDeltaDisplay,
        boolean expensesDeltaAvailable,
        BigDecimal savingsRateDeltaPercent,
        String savingsRateDeltaDisplay,
        boolean savingsRateDeltaAvailable,
        List<BaselineCategoryComparisonDto> categories
) {
}
