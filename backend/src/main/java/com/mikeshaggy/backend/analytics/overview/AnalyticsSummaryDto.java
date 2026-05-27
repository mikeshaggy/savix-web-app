package com.mikeshaggy.backend.analytics.overview;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnalyticsSummaryDto(
        OverviewStatus status,

        // Period window
        LocalDate periodStart,
        LocalDate periodEnd,
        int daysInPeriod,
        int daysElapsed,
        int daysRemaining,
        boolean projectionAvailable,

        // Forecast KPIs
        BigDecimal projectedEndBalance,
        BigDecimal safeToSpend,
        BigDecimal safeToSpendPerDay,
        BigDecimal dailyBurnRate,
        BigDecimal savingsRate,
        BigDecimal projectedTotalSpend,
        BigDecimal incomeForPeriod,

        // Top category
        Integer topCategoryId,
        String topCategoryName,
        String topCategoryEmoji,
        BigDecimal topCategoryAmount,

        // Daily stats (aggregated from heatmap)
        LocalDate highestSpendingDay,
        BigDecimal highestSpendingDayAmount,
        int activeDays,

        // Comparison
        BigDecimal expensesDeltaPercent,
        boolean comparisonAvailable
) {}
