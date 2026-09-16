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
        BigDecimal dailyBurnRate,
        BigDecimal projectedPeriodExpenses,
        BigDecimal projectedEndBalance,
        BigDecimal remainingFixedPayments,
        BigDecimal safeToSpendToday,
        BigDecimal safeToSpendPerDay,
        BigDecimal variableExpensesToDate,
        BigDecimal linkedFixedExpensesToDate,
        BigDecimal variableDailyBurnRate,
        BigDecimal projectedVariableRemaining,
        boolean projectionAvailable,
        String projectionReason
) {}
