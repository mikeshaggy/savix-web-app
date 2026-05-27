package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;

public record DashboardCycleHealthDto(
        DashboardHealthStatus status,
        BigDecimal currentBalance,
        BigDecimal safeToSpend,
        BigDecimal projectedEndBalance,
        BigDecimal spendingPaceDeltaAmount,
        BigDecimal spendingPaceDeltaPercent,
        boolean projectionAvailable,
        String projectionReason
) {
}
