package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;

@Component
public class SpendingProjectionCalculator {

    /** {@code projectionReason} for MONTHLY / CUSTOM periods and for pay cycles without a resolved state. */
    public static final String REASON_REPORTING_PERIOD = "REPORTING_PERIOD";
    /** {@code projectionReason} for LAST_PAY_CYCLE and for a PAY_CYCLE that is already {@link CycleState#CLOSED}. */
    public static final String REASON_CLOSED_CYCLE = "CLOSED_CYCLE";
    /** {@code projectionReason} for the open cycle once the expected payday has passed without a salary. */
    public static final String REASON_AWAITING_SALARY = "AWAITING_SALARY";

    /**
     * A projection exists only for the salary wallet's open pay cycle. Every other period — the calendar month,
     * a custom range, a closed cycle, a cycle awaiting its salary — is reporting-only.
     */
    public boolean isProjectionAvailable(PeriodType periodType, CycleState cycleState) {
        return periodType == PeriodType.PAY_CYCLE && cycleState == CycleState.OPEN;
    }

    /** Why {@link #isProjectionAvailable} is false; {@code null} when it is true. */
    public String projectionReason(PeriodType periodType, CycleState cycleState) {
        if (isProjectionAvailable(periodType, cycleState)) {
            return null;
        }
        return switch (periodType) {
            case PAY_CYCLE -> cycleState == null
                    ? REASON_REPORTING_PERIOD
                    : cycleState == CycleState.AWAITING_SALARY ? REASON_AWAITING_SALARY : REASON_CLOSED_CYCLE;
            case LAST_PAY_CYCLE -> REASON_CLOSED_CYCLE;
            case MONTHLY, CUSTOM -> REASON_REPORTING_PERIOD;
        };
    }

    /**
     * Legacy linear extrapolation over an open window ({@code today} inside {@code period}). Pure arithmetic:
     * the caller decides with {@link #isProjectionAvailable} whether a projection may be computed at all.
     */
    public ProjectionResult calculate(ProjectionInput input) {
        PeriodWindow period = input.period();
        LocalDate today = input.today();

        int daysInPeriod = InclusiveDateRange.daysBetween(period.startDate(), period.endDate());
        int daysElapsed = InclusiveDateRange.daysBetween(period.startDate(), today);
        int daysRemaining = Math.max(0, InclusiveDateRange.daysBetween(today.plusDays(1), period.endDate()));

        BigDecimal expensesToDate = input.expensesToDate();
        BigDecimal variableExpensesToDate = input.variableExpensesToDate();
        // Fixed (already-paid, linked) spend so far = total minus variable. Counted
        // once in the projected total but excluded from the variable burn rate.
        BigDecimal linkedFixedExpensesToDate = money(expensesToDate.subtract(variableExpensesToDate));

        // Burn rate is driven by variable spend only, so a fixed payment paid on
        // day 1 of the cycle no longer inflates the daily pace.
        BigDecimal variableDailyBurnRate = variableDailyBurnRate(variableExpensesToDate, daysElapsed);

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
                money(projectedVariableRemaining));
    }

    /** Average variable spend per elapsed day; zero when nothing has elapsed. A reporting number, not a projection. */
    public BigDecimal variableDailyBurnRate(BigDecimal variableExpensesToDate, int daysElapsed) {
        return daysElapsed == 0
                ? money(BigDecimal.ZERO)
                : variableExpensesToDate.divide(BigDecimal.valueOf(daysElapsed), SCALE, ROUNDING);
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
            BigDecimal projectedVariableRemaining) {
    }
}
