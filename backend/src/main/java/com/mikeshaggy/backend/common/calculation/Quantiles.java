package com.mikeshaggy.backend.common.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

/**
 * Deterministic money quantiles. Every result is rounded to {@link CalculationUtils#SCALE} /
 * {@link CalculationUtils#ROUNDING} once, at the end; the interpolation itself is exact.
 * <p>
 * {@link #percentile} interpolates linearly between order statistics ({@code rank = p × (n − 1)},
 * the R-7 / spreadsheet {@code PERCENTILE.INC} definition), so {@code percentile(values, 0.5)} is
 * the classic median: the middle value for an odd count, the mean of the two middle values for an
 * even count.
 */
public final class Quantiles {

    private Quantiles() {
    }

    public static BigDecimal median(List<BigDecimal> values) {
        return percentile(values, new BigDecimal("0.5"));
    }

    /**
     * @param p fraction in {@code [0, 1]}
     * @throws IllegalArgumentException when {@code values} is empty or {@code p} is out of range
     */
    public static BigDecimal percentile(List<BigDecimal> values, BigDecimal p) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("percentile of an empty series");
        }
        if (p.signum() < 0 || p.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("p must be within [0, 1]: " + p);
        }
        List<BigDecimal> sorted = values.stream().map(MoneyMath::money).sorted().toList();
        int n = sorted.size();
        if (n == 1) {
            return sorted.getFirst();
        }
        BigDecimal rank = p.multiply(BigDecimal.valueOf(n - 1L));
        int lower = rank.setScale(0, RoundingMode.FLOOR).intValueExact();
        int upper = Math.min(lower + 1, n - 1);
        BigDecimal fraction = rank.subtract(BigDecimal.valueOf(lower));
        BigDecimal low = sorted.get(lower);
        BigDecimal high = sorted.get(upper);
        return low.add(high.subtract(low).multiply(fraction)).setScale(SCALE, ROUNDING);
    }
}
