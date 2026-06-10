package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;

@Component
public class SpendingProjectionCalculator {

    public boolean isProjectionAvailable(PeriodWindow period, LocalDate today) {
        return !period.endDate().isBefore(today);
    }

    public ProjectionResult calculate(ProjectionInput input) {
        PeriodWindow period = input.period();
        LocalDate today = input.today();

        int daysInPeriod = InclusiveDateRange.daysBetween(period.startDate(), period.endDate());
        boolean projectionAvailable = isProjectionAvailable(period, today);
        int daysElapsed = projectionAvailable
                ? InclusiveDateRange.daysBetween(period.startDate(), today)
                : daysInPeriod;
        int daysRemaining = projectionAvailable
                ? Math.max(0, InclusiveDateRange.daysBetween(today.plusDays(1), period.endDate()))
                : 0;

        BigDecimal expensesToDate = input.expensesToDate();
        BigDecimal variableExpensesToDate = input.variableExpensesToDate();
        // Fixed (already-paid, linked) spend so far = total minus variable. Counted
        // once in the projected total but excluded from the variable burn rate.
        BigDecimal linkedFixedExpensesToDate = money(expensesToDate.subtract(variableExpensesToDate));

        // Burn rate is driven by variable spend only, so a fixed payment paid on
        // day 1 of the cycle no longer inflates the daily pace.
        BigDecimal variableDailyBurnRate = daysElapsed == 0
                ? money(BigDecimal.ZERO)
                : variableExpensesToDate.divide(BigDecimal.valueOf(daysElapsed), SCALE, ROUNDING);

        if (!projectionAvailable) {
            return new ProjectionResult(
                    daysInPeriod,
                    daysElapsed,
                    daysRemaining,
                    variableDailyBurnRate,
                    expensesToDate,
                    money(input.incomeForPeriod().subtract(expensesToDate)),
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    money(variableExpensesToDate),
                    linkedFixedExpensesToDate,
                    variableDailyBurnRate,
                    money(BigDecimal.ZERO),
                    false,
                    "Historical period");
        }

        BigDecimal projectedVariableRemaining = variableDailyBurnRate.multiply(BigDecimal.valueOf(daysRemaining));
        // Total projected spend keeps fixed payments at face value (paid-to-date +
        // remaining-due), counted exactly once — never re-projected by the pace.
        BigDecimal projectedPeriodExpenses = money(variableExpensesToDate
                .add(projectedVariableRemaining)
                .add(linkedFixedExpensesToDate)
                .add(input.remainingFixedPayments()));
        BigDecimal projectedEndBalance = money(input.incomeForPeriod().subtract(projectedPeriodExpenses));
        BigDecimal safeToSpendToday = money(input.walletBalance()
                .subtract(input.remainingFixedPayments())
                .subtract(projectedVariableRemaining));
        BigDecimal safeToSpendPerDay = money(safeToSpendToday.divide(
                BigDecimal.valueOf(Math.max(daysRemaining, 1)), SCALE, ROUNDING));

        return new ProjectionResult(
                daysInPeriod,
                daysElapsed,
                daysRemaining,
                variableDailyBurnRate,
                projectedPeriodExpenses,
                projectedEndBalance,
                money(input.remainingFixedPayments()),
                safeToSpendToday,
                safeToSpendPerDay,
                money(variableExpensesToDate),
                linkedFixedExpensesToDate,
                variableDailyBurnRate,
                money(projectedVariableRemaining),
                true,
                null);
    }

    public record PeriodWindow(LocalDate startDate, LocalDate endDate) {
    }

    public record ProjectionInput(
            PeriodWindow period,
            LocalDate today,
            BigDecimal walletBalance,
            BigDecimal incomeForPeriod,
            BigDecimal expensesToDate,
            BigDecimal variableExpensesToDate,
            BigDecimal remainingFixedPayments) {
    }

    public record ProjectionResult(
            int daysInPeriod,
            int daysElapsed,
            int daysRemaining,
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
            String projectionReason) {
    }
}
