package com.mikeshaggy.backend.analytics.cyclecomparison;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CycleComparisonBaselineCycleDto(
        LocalDate startDate,
        LocalDate endDate,
        LocalDate cutoffDate,
        int totalDays,
        int comparableDays,
        BigDecimal expensesAtCutoff
) {
}
