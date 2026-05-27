package com.mikeshaggy.backend.analytics.forecast;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator.PeriodWindow;
import static com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator.ProjectionInput;
import static org.assertj.core.api.Assertions.assertThat;

class SpendingProjectionCalculatorTest {

    private final SpendingProjectionCalculator calculator = new SpendingProjectionCalculator();

    @Test
    void currentPeriodProjectsExpensesAndSafeToSpendFromElapsedPace() {
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                LocalDate.of(2026, 5, 10),
                new BigDecimal("1000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("500.00"),
                new BigDecimal("200.00")));

        assertThat(result.daysInPeriod()).isEqualTo(31);
        assertThat(result.daysElapsed()).isEqualTo(10);
        assertThat(result.daysRemaining()).isEqualTo(21);
        assertThat(result.dailyBurnRate()).isEqualByComparingTo("50.00");
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("1550.00");
        assertThat(result.projectedEndBalance()).isEqualByComparingTo("1450.00");
        assertThat(result.remainingFixedPayments()).isEqualByComparingTo("200.00");
        assertThat(result.safeToSpendToday()).isEqualByComparingTo("-250.00");
        assertThat(result.projectionAvailable()).isTrue();
        assertThat(result.projectionReason()).isNull();
    }

    @Test
    void historicalPeriodReturnsActualsOnly() {
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)),
                LocalDate.of(2026, 5, 10),
                new BigDecimal("1000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("1200.00"),
                new BigDecimal("200.00")));

        assertThat(result.daysInPeriod()).isEqualTo(30);
        assertThat(result.daysElapsed()).isEqualTo(30);
        assertThat(result.daysRemaining()).isZero();
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("1200.00");
        assertThat(result.projectedEndBalance()).isEqualByComparingTo("1800.00");
        assertThat(result.remainingFixedPayments()).isEqualByComparingTo("0.00");
        assertThat(result.safeToSpendToday()).isEqualByComparingTo("0.00");
        assertThat(result.projectionAvailable()).isFalse();
        assertThat(result.projectionReason()).isEqualTo("Historical period");
    }
}
