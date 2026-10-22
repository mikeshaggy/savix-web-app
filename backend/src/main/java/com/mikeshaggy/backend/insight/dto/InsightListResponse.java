package com.mikeshaggy.backend.insight.dto;

import com.mikeshaggy.backend.common.paycycle.CycleState;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/insights?walletId=}: the active (non-dismissed) insights of the wallet's current pay cycle,
 * most severe first, then oldest first. The cycle fields are {@code null} unless {@code status} is
 * {@code AVAILABLE}.
 */
public record InsightListResponse(
        Integer walletId,
        InsightAvailability status,
        LocalDate cycleStart,
        LocalDate cycleEnd,
        CycleState cycleState,
        List<InsightResponse> insights
) {
    public InsightListResponse {
        insights = insights == null ? List.of() : List.copyOf(insights);
    }

    public static InsightListResponse unavailable(Integer walletId, InsightAvailability status) {
        return new InsightListResponse(walletId, status, null, null, null, List.of());
    }
}
