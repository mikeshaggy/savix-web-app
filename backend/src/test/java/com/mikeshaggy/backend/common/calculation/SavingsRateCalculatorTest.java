package com.mikeshaggy.backend.common.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SavingsRateCalculatorTest {

    @Test
    void fromIncomeAndExpenses_returnsSavedShareOfIncome() {
        BigDecimal result = SavingsRateCalculator.fromIncomeAndExpenses(
                new BigDecimal("1000.00"),
                new BigDecimal("250.00"));

        assertThat(result).isEqualByComparingTo("75.00");
    }

    @Test
    void fromIncomeAndExpenses_whenExpensesExceedIncomeReturnsNegativeRate() {
        BigDecimal result = SavingsRateCalculator.fromIncomeAndExpenses(
                new BigDecimal("1000.00"),
                new BigDecimal("1250.00"));

        assertThat(result).isEqualByComparingTo("-25.00");
    }

    @Test
    void fromIncomeAndExpenses_whenIncomeIsZeroReturnsScaledZero() {
        BigDecimal result = SavingsRateCalculator.fromIncomeAndExpenses(
                BigDecimal.ZERO,
                new BigDecimal("250.00"));

        assertThat(result).isEqualByComparingTo("0.00");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void fromSaved_supportsHigherScaleForDeltaCalculations() {
        BigDecimal result = SavingsRateCalculator.fromSaved(
                new BigDecimal("3.00"),
                new BigDecimal("1.00"),
                6);

        assertThat(result).isEqualByComparingTo("33.333333");
    }
}
