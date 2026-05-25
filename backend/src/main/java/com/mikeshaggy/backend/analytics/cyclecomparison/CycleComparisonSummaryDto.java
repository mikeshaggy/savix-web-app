package com.mikeshaggy.backend.analytics.cyclecomparison;

import java.math.BigDecimal;

public record CycleComparisonSummaryDto(
        BigDecimal currentExpenses,
        BigDecimal baselineAverageExpenses,
        BigDecimal deltaAmount,
        BigDecimal deltaPercent,
        CycleComparisonStatus status
) {
}
