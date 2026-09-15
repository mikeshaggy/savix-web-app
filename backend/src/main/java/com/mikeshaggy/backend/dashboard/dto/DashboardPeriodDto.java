package com.mikeshaggy.backend.dashboard.dto;

import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.period.PeriodType;

import java.time.LocalDate;

/**
 * The dashboard's resolved period. {@code cycleState}, {@code expectedPaydayDate} and {@code salaryWallet} are the
 * pay-cycle metadata of {@code PeriodDto} ({@code null} for MONTHLY / CUSTOM and for legacy resolver paths);
 * {@code reporting} is {@code true} for MONTHLY, CUSTOM and LAST_PAY_CYCLE — periods that carry actuals only and
 * never a health verdict or a projection.
 */
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
        LocalDate compareEndDate,
        CycleState cycleState,
        LocalDate expectedPaydayDate,
        Boolean salaryWallet,
        boolean reporting
) {
}
