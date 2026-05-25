package com.mikeshaggy.backend.analytics.cyclecomparison;

import java.time.LocalDate;

public record CycleComparisonCycleDto(
        LocalDate startDate,
        LocalDate endDate,
        LocalDate cutoffDate,
        int dayIndex,
        int elapsedDays,
        int totalDays
) {
}
