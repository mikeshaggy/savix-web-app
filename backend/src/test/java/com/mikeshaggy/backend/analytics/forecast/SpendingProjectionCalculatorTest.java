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
                new BigDecimal("500.00"),
                new BigDecimal("200.00")));

        assertThat(result.daysInPeriod()).isEqualTo(31);
        assertThat(result.daysElapsed()).isEqualTo(10);
        assertThat(result.daysRemaining()).isEqualTo(21);
        assertThat(result.dailyBurnRate()).isEqualByComparingTo("50.00");
        // variable(500) + projectedVariableRemaining(50*21=1050) + linkedFixed(0) + remainingFixed(200)
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("1750.00");
        assertThat(result.projectedEndBalance()).isEqualByComparingTo("1250.00");
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
                new BigDecimal("200.00"),
                new BigDecimal("300.00")));

        assertThat(result.safeToSpendToday()).isEqualByComparingTo("0.00");
        assertThat(result.safeToSpendPerDay()).isEqualByComparingTo("0.00");
    }

    // ----- Variable / fixed split (linked FixedPaymentOccurrence transactions) -----

    @Test
    void day1OfCycle_rentPaidAsLinkedFixed_doesNotExplodeBurnRate() {
        // Day 1 of a 30-day cycle. Salary received; rent (2000) paid as a linked
        // fixed payment; only 40 of genuinely variable spend so far.
        // expensesToDate = 2040, variableExpensesToDate = 40.
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30)),
                LocalDate.of(2026, 5, 1),
                new BigDecimal("3000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("2040.00"),
                new BigDecimal("40.00"),
                new BigDecimal("0.00")));

        // Burn rate is driven by the 40 variable spend, not by the 2000 rent.
        assertThat(result.variableDailyBurnRate()).isEqualByComparingTo("40.00");
        assertThat(result.dailyBurnRate()).isEqualByComparingTo("40.00");
        assertThat(result.linkedFixedExpensesToDate()).isEqualByComparingTo("2000.00");
        // projected total = variable(40) + variableRemaining(40*29=1160) + linkedFixed(2000) + remainingFixed(0)
        // = 3200. The rent is counted ONCE (2000), not extrapolated to ~60k.
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("3200.00");
    }

    @Test
    void linkedFixedPayment_remainsIncludedInProjectedTotal() {
        // Variable spend zero, but a 1500 fixed payment was paid (linked).
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                LocalDate.of(2026, 5, 10),
                new BigDecimal("3000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("1500.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00")));

        assertThat(result.variableDailyBurnRate()).isEqualByComparingTo("0.00");
        // The paid fixed payment must still appear in the projected total.
        assertThat(result.linkedFixedExpensesToDate()).isEqualByComparingTo("1500.00");
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("1500.00");
    }

    @Test
    void remainingUnpaidFixedPayments_includedOnce() {
        // No spend yet at all; a 900 fixed payment is still due later in the period.
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                LocalDate.of(2026, 5, 10),
                new BigDecimal("3000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal("900.00")));

        assertThat(result.variableDailyBurnRate()).isEqualByComparingTo("0.00");
        // Remaining fixed counted exactly once in the projected total.
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("900.00");
        assertThat(result.remainingFixedPayments()).isEqualByComparingTo("900.00");
    }

    @Test
    void onlyFixedExpenses_noVariable_givesZeroVariableBurnRate() {
        // expensesToDate fully made of linked fixed payments.
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                LocalDate.of(2026, 5, 10),
                new BigDecimal("3000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("1200.00"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00")));

        assertThat(result.variableDailyBurnRate()).isEqualByComparingTo("0.00");
        assertThat(result.dailyBurnRate()).isEqualByComparingTo("0.00");
        assertThat(result.projectedVariableRemaining()).isEqualByComparingTo("0.00");
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("1200.00");
    }

    @Test
    void paidAndRemainingFixed_notDoubleCountedAgainstVariablePace() {
        // 1000 fixed already paid (linked), 500 fixed still due, 300 genuinely variable.
        // expensesToDate = 1300 (1000 fixed + 300 variable), variable = 300.
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                LocalDate.of(2026, 5, 10),
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("1300.00"),
                new BigDecimal("300.00"),
                new BigDecimal("500.00")));

        // pace from variable only: 300 / 10 = 30
        assertThat(result.variableDailyBurnRate()).isEqualByComparingTo("30.00");
        // projected = variable(300) + variableRemaining(30*21=630) + linkedFixed(1000) + remainingFixed(500) = 2430
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("2430.00");
        // safe-to-spend subtracts remaining fixed + projected variable remaining only,
        // never the already-paid fixed: 5000 - 500 - 630 = 3870
        assertThat(result.safeToSpendToday()).isEqualByComparingTo("3870.00");
    }

    @Test
    void unlinkedManualExpenses_stillDriveVariableBurnRate() {
        // All expenses are variable (no linked fixed). Variable pace reflects them.
        SpendingProjectionCalculator.ProjectionResult result = calculator.calculate(new ProjectionInput(
                new PeriodWindow(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)),
                LocalDate.of(2026, 5, 10),
                new BigDecimal("3000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("800.00"),
                new BigDecimal("800.00"),
                new BigDecimal("0.00")));

        // 800 / 10 = 80 variable burn rate
        assertThat(result.variableDailyBurnRate()).isEqualByComparingTo("80.00");
        assertThat(result.linkedFixedExpensesToDate()).isEqualByComparingTo("0.00");
        // projected = 800 + 80*21(1680) = 2480
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("2480.00");
    }
}
