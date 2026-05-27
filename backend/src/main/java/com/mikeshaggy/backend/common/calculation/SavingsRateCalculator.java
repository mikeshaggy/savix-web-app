package com.mikeshaggy.backend.common.calculation;

import java.math.BigDecimal;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

public final class SavingsRateCalculator {

    private SavingsRateCalculator() {
    }

    public static BigDecimal fromSaved(BigDecimal income, BigDecimal saved) {
        return fromSaved(income, saved, SCALE);
    }

    public static BigDecimal fromSaved(BigDecimal income, BigDecimal saved, int scale) {
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(scale, ROUNDING);
        }
        return saved.multiply(HUNDRED).divide(income, scale, ROUNDING);
    }

    public static BigDecimal fromIncomeAndExpenses(BigDecimal income, BigDecimal expenses) {
        return fromIncomeAndExpenses(income, expenses, SCALE);
    }

    public static BigDecimal fromIncomeAndExpenses(BigDecimal income, BigDecimal expenses, int scale) {
        return fromSaved(income, income.subtract(expenses), scale);
    }
}
