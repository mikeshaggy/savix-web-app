package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.common.period.PeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Forecast figures for one period.
 *
 * <p>{@code expensesToDate}, {@code variableExpensesToDate} and {@code linkedFixedExpensesToDate} are ledger
 * sums over transactions <em>dated</em> in {@code [startDate, today]}, with the invariant
 * {@code expensesToDate = variableExpensesToDate + linkedFixedExpensesToDate} so every PLN that left the wallet is
 * counted exactly once in {@code projectedPeriodExpenses}. {@code linkedFixedExpensesToDate} is therefore
 * <em>not</em> the fixed-payments "Paid" total ({@code FixedSummaryDto.paidAmount}), which is an obligation
 * view keyed on the occurrence due date (Stage 3.5 decision B): an obligation of this cycle paid before the
 * cycle started counts there but not here, and a previous-cycle obligation paid in this cycle counts here but
 * not there. {@code remainingFixedPayments} stays obligation-based (unpaid occurrences due in the cycle).
 *
 * <p>{@code forecast} (Stage 4.7) is Forecast v2 for the salary wallet's current pay cycle — populated only while
 * {@code app.features.forecast-v2} is on and the period is applicable, {@code null} otherwise. The
 * {@code @Deprecated} fields are the legacy linear extrapolation (§9 of the plan): still computed and served
 * during the comparison window, deleted once v2 is the default.
 */
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
        @Deprecated BigDecimal dailyBurnRate,
        @Deprecated BigDecimal projectedPeriodExpenses,
        @Deprecated BigDecimal projectedEndBalance,
        BigDecimal remainingFixedPayments,
        @Deprecated BigDecimal safeToSpendToday,
        @Deprecated BigDecimal safeToSpendPerDay,
        BigDecimal variableExpensesToDate,
        BigDecimal linkedFixedExpensesToDate,
        @Deprecated BigDecimal variableDailyBurnRate,
        @Deprecated BigDecimal projectedVariableRemaining,
        boolean projectionAvailable,
        String projectionReason,
        ForecastV2Dto forecast
) {
    /** The same projection carrying {@code forecast}. */
    public SpendingProjectionDto withForecast(ForecastV2Dto forecast) {
        return new SpendingProjectionDto(periodType, periodLabel, startDate, endDate, daysInPeriod, daysElapsed,
                daysRemaining, incomeToDate, incomeForPeriod, expensesToDate, dailyBurnRate,
                projectedPeriodExpenses, projectedEndBalance, remainingFixedPayments, safeToSpendToday,
                safeToSpendPerDay, variableExpensesToDate, linkedFixedExpensesToDate, variableDailyBurnRate,
                projectedVariableRemaining, projectionAvailable, projectionReason, forecast);
    }
}
