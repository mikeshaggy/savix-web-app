package com.mikeshaggy.backend.common.calculation;

import java.math.BigDecimal;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

public final class DeltaCalculator {

    private DeltaCalculator() {
    }

    public static Delta amountAndPercent(BigDecimal current, BigDecimal baseline, ZeroBaselineMode zeroBaselineMode) {
        BigDecimal deltaAmount = MoneyMath.money(current.subtract(baseline));
        BigDecimal percent = percent(current, baseline, zeroBaselineMode);
        return new Delta(deltaAmount, percent, percent != null);
    }

    public static BigDecimal percent(BigDecimal current, BigDecimal baseline, ZeroBaselineMode zeroBaselineMode) {
        if (baseline.compareTo(BigDecimal.ZERO) == 0) {
            if (zeroBaselineMode == ZeroBaselineMode.ZERO_WHEN_BOTH_ZERO_NA_OTHERWISE
                    && current.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
            }
            return null;
        }
        return current.subtract(baseline)
                .multiply(HUNDRED)
                .divide(baseline, SCALE, ROUNDING);
    }

    public static String signedPercentText(BigDecimal value) {
        return "%s%s%%".formatted(value.signum() > 0 ? "+" : "", value.setScale(SCALE, ROUNDING));
    }

    public enum ZeroBaselineMode {
        ZERO_WHEN_BOTH_ZERO_NA_OTHERWISE,
        NULL_ON_ZERO_BASELINE
    }

    public record Delta(BigDecimal amount, BigDecimal percent, boolean percentAvailable) {
    }
}
