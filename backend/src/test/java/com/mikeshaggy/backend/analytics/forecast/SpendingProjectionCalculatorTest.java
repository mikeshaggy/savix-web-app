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
        // -250.00 / 21 = -11.90 (HALF_UP)
        assertThat(result.safeToSpendPerDay()).isEqualByComparingTo("-11.90");
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
        // daysRemaining = 0 → max(0,1) = 1; 0.00 / 1 = 0.00
        assertThat(result.safeToSpendPerDay()).isEqualByComparingTo("0.00");
        assertThat(result.projectionAvailable()).isFalse();
        assertThat(result.projectionReason()).isEqualTo("Historical period");
    }

    @Test
    void oneDayRemaining_perDayEqualsTotal() {
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10)),
                LocalDate.of(2026, 5, 9),
                new BigDecimal("500.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("400.00"),
                new BigDecimal("50.00")));

        assertThat(result.daysRemaining()).isEqualTo(1);
        assertThat(result.safeToSpendToday()).isEqualByComparingTo(result.safeToSpendPerDay());
    }

    @Test
    void negativeSafeToSpend_perDayIsNegative() {
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                LocalDate.of(2026, 5, 15),
                new BigDecimal("100.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("500.00")));

        assertThat(result.safeToSpendToday()).isNegative();
        assertThat(result.safeToSpendPerDay()).isNegative();
        assertThat(result.safeToSpendPerDay().compareTo(result.safeToSpendToday())).isGreaterThan(0);
    }

    @Test
    void zeroSafeToSpend_perDayIsZero() {
        // Period: May 1–20 (20 days inclusive). Today: May 10.
        // daysElapsed=10, daysRemaining=10, burnRate=200/10=20, projectedRemaining=20*10=200
        // walletBalance(500) - remainingFixed(300) - projectedRemaining(200) = 0
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 20)),
                LocalDate.of(2026, 5, 10),
                new BigDecimal("500.00"),
                new BigDecimal("2000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("300.00")));

        assertThat(result.safeToSpendToday()).isEqualByComparingTo("0.00");
        assertThat(result.safeToSpendPerDay()).isEqualByComparingTo("0.00");
    }
}
