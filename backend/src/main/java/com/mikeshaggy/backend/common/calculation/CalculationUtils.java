package com.mikeshaggy.backend.common.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CalculationUtils {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    private CalculationUtils() {
    }
}
