package com.mikeshaggy.backend.analytics.overview;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PeriodOverviewDto(
        LocalDate startDate,
        LocalDate endDate,
        Integer walletId,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal balance,
        BigDecimal savingsRate,
        long transactionCount,
        BigDecimal avgDailySpending,
        int daysInPeriod,
        int daysElapsed
) {}
