package com.mikeshaggy.backend.dashboard.dto;

import com.mikeshaggy.backend.common.period.PeriodType;

import java.time.LocalDate;

public record DashboardPeriodDto(
        PeriodType type,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate billingEndDate,
        LocalDate asOfDate,
        LocalDate cutoffDate,
        int daysInPeriod,
        int daysElapsed,
        int daysRemaining,
        boolean comparisonAvailable,
        LocalDate compareStartDate,
        LocalDate compareEndDate
) {
}
