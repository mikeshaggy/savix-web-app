package com.mikeshaggy.backend.common.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyMathTest {

    @Test
    void money_normalizesNullToScaledZero() {
        assertThat(MoneyMath.money(null)).isEqualByComparingTo("0.00");
        assertThat(MoneyMath.money(null).scale()).isEqualTo(2);
    }

    @Test
    void money_appliesTwoDecimalHalfUpScale() {
        assertThat(MoneyMath.money(new BigDecimal("1.235"))).isEqualByComparingTo("1.24");
    }

    @Test
    void average_dividesByExplicitDivisorIncludingMissingBuckets() {
        BigDecimal result = MoneyMath.average(List.of(new BigDecimal("30.00")), 3);

        assertThat(result).isEqualByComparingTo("10.00");
    }

    @Test
    void average_withZeroDivisorReturnsScaledZero() {
        BigDecimal result = MoneyMath.average(List.of(new BigDecimal("30.00")), 0);

        assertThat(result).isEqualByComparingTo("0.00");
        assertThat(result.scale()).isEqualTo(2);
    }
}
