package com.mikeshaggy.backend.common.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantilesTest {

    @Test
    void medianOfOddCountIsTheMiddleValueRegardlessOfInputOrder() {
        assertThat(Quantiles.median(money("30", "10", "20"))).isEqualByComparingTo("20.00");
    }

    @Test
    void medianOfEvenCountIsTheMeanOfTheTwoMiddleValuesRoundedHalfUp() {
        assertThat(Quantiles.median(money("10", "20", "30", "100"))).isEqualByComparingTo("25.00");
        assertThat(Quantiles.median(money("0.01", "0.02"))).isEqualByComparingTo("0.02");
    }

    @Test
    void singleValueIsEveryQuantile() {
        assertThat(Quantiles.percentile(money("42"), new BigDecimal("0.25"))).isEqualByComparingTo("42.00");
        assertThat(Quantiles.percentile(money("42"), BigDecimal.ONE)).isEqualByComparingTo("42.00");
    }

    @Test
    void percentilesInterpolateBetweenOrderStatistics() {
        List<BigDecimal> six = money("2400", "1200", "1560", "1441.04", "1500", "1200");

        assertThat(Quantiles.percentile(six, new BigDecimal("0.25"))).isEqualByComparingTo("1260.26");
        assertThat(Quantiles.percentile(six, new BigDecimal("0.5"))).isEqualByComparingTo("1470.52");
        assertThat(Quantiles.percentile(six, new BigDecimal("0.75"))).isEqualByComparingTo("1545.00");
        assertThat(Quantiles.percentile(six, BigDecimal.ZERO)).isEqualByComparingTo("1200.00");
        assertThat(Quantiles.percentile(six, BigDecimal.ONE)).isEqualByComparingTo("2400.00");
    }

    @Test
    void rejectsEmptySeriesAndOutOfRangeFraction() {
        assertThatThrownBy(() -> Quantiles.median(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quantiles.percentile(money("1"), new BigDecimal("1.5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<BigDecimal> money(String... values) {
        return Stream.of(values).map(BigDecimal::new).toList();
    }
}
