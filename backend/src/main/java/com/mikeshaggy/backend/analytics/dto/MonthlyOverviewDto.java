package com.mikeshaggy.backend.analytics.dto;

import java.math.BigDecimal;

public record MonthlyOverviewDto(
        String month,
        Integer walletId,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal balance,
        BigDecimal savingsRate,
        long transactionCount,
        BigDecimal avgDailySpending,
        int daysInMonth,
        int daysElapsed
) {}
