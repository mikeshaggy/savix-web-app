package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.common.period.PeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendingProjectionDto(
        PeriodType periodType,
        String periodLabel,
        LocalDate startDate,
        LocalDate endDate,
        int daysInPeriod,
        int daysElapsed,
        int daysRemaining,
        BigDecimal incomeToDate,
        BigDecimal incomeForPeriod,
        BigDecimal expensesToDate,
        BigDecimal dailyBurnRate,
        BigDecimal projectedPeriodExpenses,
        BigDecimal projectedEndBalance,
        BigDecimal remainingFixedPayments,
        BigDecimal safeToSpendToday,
        BigDecimal safeToSpendPerDay,
        boolean projectionAvailable,
        String projectionReason
) {}
