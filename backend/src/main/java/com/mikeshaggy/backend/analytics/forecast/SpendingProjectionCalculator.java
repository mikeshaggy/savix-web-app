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

        BigDecimal dailyBurnRate = daysElapsed == 0
                ? money(BigDecimal.ZERO)
                : input.expensesToDate().divide(BigDecimal.valueOf(daysElapsed), SCALE, ROUNDING);

        if (!projectionAvailable) {
            return new ProjectionResult(
                    daysInPeriod,
                    daysElapsed,
                    daysRemaining,
                    dailyBurnRate,
                    input.expensesToDate(),
                    money(input.incomeForPeriod().subtract(input.expensesToDate())),
                    money(BigDecimal.ZERO),
                    money(BigDecimal.ZERO),
                    false,
                    "Historical period");
        }

        BigDecimal projectedPeriodExpenses = money(dailyBurnRate.multiply(BigDecimal.valueOf(daysInPeriod)));
        BigDecimal projectedEndBalance = money(input.incomeForPeriod().subtract(projectedPeriodExpenses));
        BigDecimal projectedRemainingVariableSpend = dailyBurnRate.multiply(BigDecimal.valueOf(daysRemaining));
        BigDecimal safeToSpendToday = money(input.walletBalance()
                .subtract(input.remainingFixedPayments())
                .subtract(projectedRemainingVariableSpend));

        return new ProjectionResult(
                daysInPeriod,
                daysElapsed,
                daysRemaining,
                dailyBurnRate,
                projectedPeriodExpenses,
                projectedEndBalance,
                money(input.remainingFixedPayments()),
                safeToSpendToday,
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
            boolean projectionAvailable,
            String projectionReason) {
    }
}
