package com.mikeshaggy.backend.common.calculation;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

public final class MoneyMath {

    private MoneyMath() {
    }

    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal average(Collection<BigDecimal> values, int divisor) {
        if (divisor == 0) {
            return zero();
        }

        BigDecimal total = values.stream()
                .filter(Objects::nonNull)
                .map(MoneyMath::money)
                .reduce(zero(), BigDecimal::add)
                .setScale(SCALE, ROUNDING);

        return total.divide(BigDecimal.valueOf(divisor), SCALE, ROUNDING);
    }
}
