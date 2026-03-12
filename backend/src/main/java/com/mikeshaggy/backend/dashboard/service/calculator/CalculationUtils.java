package com.mikeshaggy.backend.dashboard.service.calculator;

import com.mikeshaggy.backend.dashboard.dto.PercentageChangeDto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CalculationUtils {

    static final int SCALE = 2;
    static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    static final BigDecimal HUNDRED = new BigDecimal("100");

    private CalculationUtils() {
    }

    public static PercentageChangeDto percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return new PercentageChangeDto(BigDecimal.ZERO.setScale(SCALE, ROUNDING), true);
        }

        BigDecimal change = current.subtract(previous)
                .multiply(HUNDRED)
                .divide(previous.abs(), SCALE, ROUNDING);

        return new PercentageChangeDto(change.abs(), change.compareTo(BigDecimal.ZERO) >= 0);
    }
}
