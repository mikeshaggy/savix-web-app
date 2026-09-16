package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CurrentPace;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stage 4.4 — robust current pace. Stage 4.5+ (blend, range, status) is not part of this test yet. */
class ForecastV2CalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 9);

    private final ForecastV2Calculator calculator = new ForecastV2Calculator();

    @Test
    void oddNumberOfDaysUsesTheMiddleValue() {
        CurrentPace pace = calculator.currentPace(daily("10.00", "30.00", "20.00"), 5);

        assertThat(pace.daysElapsed()).isEqualTo(3);
        assertThat(pace.variableToDate()).isEqualByComparingTo("60.00");
        assertThat(pace.trimmedDailyPace()).isEqualByComparingTo("20.00");
        assertThat(pace.rawDailyBurnRate()).isEqualByComparingTo("20.00");
        assertThat(pace.paceProjection()).isEqualByComparingTo("100.00");
    }

    @Test
    void evenNumberOfDaysAveragesTheTwoMiddleValues() {
        CurrentPace pace = calculator.currentPace(daily("10.00", "20.00", "30.00", "100.00"), 10);

        assertThat(pace.trimmedDailyPace()).isEqualByComparingTo("25.00");
        assertThat(pace.rawDailyBurnRate()).isEqualByComparingTo("40.00");
        assertThat(pace.paceProjection()).isEqualByComparingTo("250.00");
    }

    @Test
    void zeroSpendDaysStayInTheSeriesAndPullTheMedianDown() {
        CurrentPace withZeros = calculator.currentPace(daily("0.00", "0.00", "50.00", "60.00", "70.00"), 20);
        CurrentPace mostlyZeros = calculator.currentPace(daily("0.00", "0.00", "0.00", "50.00"), 20);

        assertThat(withZeros.daysElapsed()).isEqualTo(5);
        assertThat(withZeros.trimmedDailyPace()).isEqualByComparingTo("50.00");
        assertThat(withZeros.rawDailyBurnRate()).isEqualByComparingTo("36.00");
        assertThat(mostlyZeros.trimmedDailyPace()).isEqualByComparingTo("0.00");
        assertThat(mostlyZeros.rawDailyBurnRate()).isEqualByComparingTo("12.50");
        assertThat(mostlyZeros.paceProjection()).isEqualByComparingTo("0.00");
    }

    @Test
    void dayOneSingleObservationHasNoDivisionByZero() {
        CurrentPace pace = calculator.currentPace(daily("94.98"), 29);

        assertThat(pace.daysElapsed()).isEqualTo(1);
        assertThat(pace.trimmedDailyPace()).isEqualByComparingTo("94.98");
        assertThat(pace.rawDailyBurnRate()).isEqualByComparingTo("94.98");
        assertThat(pace.paceProjection()).isEqualByComparingTo("2754.42");
    }

    @Test
    void largeOneOffMovesTheRawAverageButNotTheMedian() {
        CurrentPace pace = calculator.currentPace(daily("50.00", "55.00", "60.00", "1000.00"), 10);

        assertThat(pace.trimmedDailyPace()).isEqualByComparingTo("57.50");
        assertThat(pace.rawDailyBurnRate()).isEqualByComparingTo("291.25");
        assertThat(pace.paceProjection()).isEqualByComparingTo("575.00");
    }

    @Test
    void fixtureLikeSeriesMatchesThePlannedTrimmedAndRawValues() {
        // Sep 9–14 daily pace-eligible variable spend of the Sep 14 fixture (plan Stage 4 tests)
        CurrentPace pace = calculator.currentPace(
                daily("94.98", "335.64", "361.64", "316.76", "443.00", "0.00"), 24);

        assertThat(pace.trimmedDailyPace()).isEqualByComparingTo("326.20");
        assertThat(pace.rawDailyBurnRate()).isEqualByComparingTo("258.67");
        assertThat(pace.paceProjection()).isEqualByComparingTo(new BigDecimal("326.20").multiply(BigDecimal.valueOf(24)));
        assertThat(pace.paceProjection()).isEqualByComparingTo("7828.80");
    }

    @Test
    void zeroDaysRemainingProjectsZero() {
        CurrentPace pace = calculator.currentPace(daily("10.00", "20.00"), 0);

        assertThat(pace.trimmedDailyPace()).isEqualByComparingTo("15.00");
        assertThat(pace.paceProjection()).isEqualByComparingTo("0.00");
    }

    @Test
    void resultsAreRoundedHalfUpToTwoDecimals() {
        CurrentPace pace = calculator.currentPace(daily("0.01", "0.02", "0.02"), 3);

        assertThat(pace.trimmedDailyPace()).isEqualByComparingTo("0.02");
        assertThat(pace.rawDailyBurnRate()).isEqualByComparingTo("0.02");   // 0.01666… → 0.02
        assertThat(pace.rawDailyBurnRate().scale()).isEqualTo(2);
        assertThat(pace.paceProjection().scale()).isEqualTo(2);
    }

    @Test
    void rejectsEmptySeriesAndNegativeHorizon() {
        assertThatThrownBy(() -> calculator.currentPace(List.of(), 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.currentPace(daily("1.00"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<DailyTotal> daily(String... amounts) {
        List<DailyTotal> totals = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            totals.add(new DailyTotal(START.plusDays(i), new BigDecimal(amounts[i])));
        }
        return totals;
    }
}
