package com.mikeshaggy.backend.common.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.mikeshaggy.backend.common.calculation.DeltaCalculator.ZeroBaselineMode.NULL_ON_ZERO_BASELINE;
import static com.mikeshaggy.backend.common.calculation.DeltaCalculator.ZeroBaselineMode.ZERO_WHEN_BOTH_ZERO_NA_OTHERWISE;
import static org.assertj.core.api.Assertions.assertThat;

class DeltaCalculatorTest {

    @Test
    void amountAndPercent_calculatesSignedDeltaPercentAgainstBaseline() {
        DeltaCalculator.Delta result = DeltaCalculator.amountAndPercent(
                new BigDecimal("125.00"),
                new BigDecimal("100.00"),
                NULL_ON_ZERO_BASELINE);

        assertThat(result.amount()).isEqualByComparingTo("25.00");
        assertThat(result.percent()).isEqualByComparingTo("25.00");
        assertThat(result.percentAvailable()).isTrue();
    }

    @Test
    void amountAndPercent_canRepresentNewSpendWithNullPercent() {
        DeltaCalculator.Delta result = DeltaCalculator.amountAndPercent(
                new BigDecimal("25.00"),
                BigDecimal.ZERO,
                NULL_ON_ZERO_BASELINE);

        assertThat(result.amount()).isEqualByComparingTo("25.00");
        assertThat(result.percent()).isNull();
        assertThat(result.percentAvailable()).isFalse();
    }

    @Test
    void amountAndPercent_preservesBaselineBehaviorWhereBothZeroIsAvailableZeroPercent() {
        DeltaCalculator.Delta result = DeltaCalculator.amountAndPercent(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                ZERO_WHEN_BOTH_ZERO_NA_OTHERWISE);

        assertThat(result.amount()).isEqualByComparingTo("0.00");
        assertThat(result.percent()).isEqualByComparingTo("0.00");
        assertThat(result.percentAvailable()).isTrue();
    }

    @Test
    void signedPercentTextPrefixesPositiveValuesOnly() {
        assertThat(DeltaCalculator.signedPercentText(new BigDecimal("12.345"))).isEqualTo("+12.35%");
        assertThat(DeltaCalculator.signedPercentText(new BigDecimal("-12.345"))).isEqualTo("-12.35%");
        assertThat(DeltaCalculator.signedPercentText(BigDecimal.ZERO)).isEqualTo("0.00%");
    }
}
