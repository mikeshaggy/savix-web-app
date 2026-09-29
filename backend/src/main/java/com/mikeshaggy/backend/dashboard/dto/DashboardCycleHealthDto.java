package com.mikeshaggy.backend.dashboard.dto;

import com.mikeshaggy.backend.analytics.forecast.ForecastConfidence;
import com.mikeshaggy.backend.analytics.forecast.ForecastStatus;

import java.math.BigDecimal;

/**
 * The dashboard's cycle-health verdict for the salary wallet's open pay cycle.
 *
 * <p>Two verdict families coexist during the Forecast v2 rollout (Stage 4.7). With {@code forecast-v2} off the
 * legacy fields carry the verdict ({@code status}, {@code safeToSpend*}, {@code projectedEndBalance}) and the
 * v2 fields are {@code null}. With the flag on, {@code status} is {@code null} and {@code forecastStatus}
 * ({@code FINE / TIGHT / SHORT}) carries it, with the v2 figures taken from the same {@code ForecastService}
 * result the projection endpoint serves; the legacy figures stay populated for the comparison window. A cycle
 * awaiting its salary and every reporting period carry no verdict in either family.
 */
public record DashboardCycleHealthDto(
        DashboardHealthStatus status,
        BigDecimal currentBalance,
        BigDecimal safeToSpend,
        BigDecimal safeToSpendPerDay,
        BigDecimal projectedEndBalance,
        BigDecimal spendingPaceDeltaAmount,
        BigDecimal spendingPaceDeltaPercent,
        boolean projectionAvailable,
        String projectionReason,
        ForecastStatus forecastStatus,
        BigDecimal discretionaryNow,
        BigDecimal discretionaryPerDay,
        BigDecimal expectedEndBalanceTypical,
        BigDecimal expectedEndBalanceLow,
        BigDecimal expectedEndBalanceHigh,
        ForecastConfidence confidence,
        Integer oneOffCount,
        BigDecimal committed
) {
    /** The legacy shape: no Forecast v2 fields. */
    public DashboardCycleHealthDto(DashboardHealthStatus status,
                                   BigDecimal currentBalance,
                                   BigDecimal safeToSpend,
                                   BigDecimal safeToSpendPerDay,
                                   BigDecimal projectedEndBalance,
                                   BigDecimal spendingPaceDeltaAmount,
                                   BigDecimal spendingPaceDeltaPercent,
                                   boolean projectionAvailable,
                                   String projectionReason) {
        this(status, currentBalance, safeToSpend, safeToSpendPerDay, projectedEndBalance,
                spendingPaceDeltaAmount, spendingPaceDeltaPercent, projectionAvailable, projectionReason,
                null, null, null, null, null, null, null, null, null);
    }
}
