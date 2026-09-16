package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CommittedOccurrence;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CurrentPace;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.ForecastInput;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.ForecastV2;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.HistoricalBaseline;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import com.mikeshaggy.backend.regression.September2026Fixture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stage 4.4 — robust current pace; Stage 4.5 — history weight, blend, range, status (nested). */
class ForecastV2CalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);
    private static final LocalDate CYCLE_END = LocalDate.of(2026, 10, 8);

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

    // ---------------------------------------------------------------------------------------------
    // Stage 4.5
    // ---------------------------------------------------------------------------------------------

    @Nested
    class HistoryWeight {

        @Test
        void zeroHistoryWeighsNothing() {
            assertThat(calculator.historyWeight(0, 6, 30)).isEqualByComparingTo("0");
            assertThat(calculator.historyWeight(0, 1, 30)).isEqualByComparingTo("0");
        }

        @Test
        void oneOrTwoCyclesAreCappedAtOneHalf() {
            assertThat(calculator.historyWeight(1, 6, 30)).isEqualByComparingTo("0.5");   // clamp would give 0.8
            assertThat(calculator.historyWeight(2, 6, 30)).isEqualByComparingTo("0.5");
            assertThat(calculator.historyWeight(1, 1, 30)).isEqualByComparingTo("0.5");   // clamp would give 0.9
            assertThat(calculator.historyWeight(2, 25, 30)).isEqualByComparingTo("0.3");  // below the cap: floor wins
        }

        @Test
        void threeOrMoreCyclesReachTheFullClampRange() {
            assertThat(calculator.historyWeight(3, 1, 30)).isEqualByComparingTo("0.9");   // 0.9667 → ceiling
            assertThat(calculator.historyWeight(6, 3, 30)).isEqualByComparingTo("0.9");   // exactly 0.9
            assertThat(calculator.historyWeight(3, 29, 30)).isEqualByComparingTo("0.3");  // 0.0333 → floor
            assertThat(calculator.historyWeight(3, 30, 30)).isEqualByComparingTo("0.3");  // last day
            assertThat(calculator.historyWeight(3, 35, 30)).isEqualByComparingTo("0.3");  // awaiting salary: d > D
            assertThat(calculator.historyWeight(3, 6, 30)).isEqualByComparingTo("0.8");
        }

        @Test
        void elapsedRatioIsDecimalNotIntegerDivision() {
            // 7/30 = 0.2333… → 0.7667; integer division (7 / 30 == 0) would yield 1 − 0 = 1 → clamped 0.9
            assertThat(calculator.historyWeight(3, 7, 30)).isEqualByComparingTo("0.7667");
            // 15/30 = 0.5 exactly; integer division would again produce 0.9
            assertThat(calculator.historyWeight(3, 15, 30)).isEqualByComparingTo("0.5");
            assertThat(calculator.historyWeight(3, 15, 30)).isNotEqualByComparingTo("0.9");
            assertThat(calculator.historyWeight(3, 6, 30).scale()).isEqualTo(4);
        }

        @Test
        void rejectsANonPositiveCycleLengthAndANegativeDayIndex() {
            assertThatThrownBy(() -> calculator.historyWeight(3, 1, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calculator.historyWeight(3, -1, 30)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class BlendAndRange {

        @Test
        void zeroHistoryUsesThePaceProjectionAloneWithLowConfidence() {
            CurrentPace pace = calculator.currentPace(
                    daily("94.98", "335.64", "361.64", "316.76", "443.00", "0.00"), 24);
            ForecastV2 forecast = calculator.forecast(input("5000.00", 6, 30, 24, List.of(), pace, noHistory()));

            assertThat(forecast.historyWeight()).isEqualByComparingTo("0");
            assertThat(forecast.confidence()).isEqualTo(ForecastConfidence.LOW);
            assertThat(forecast.expectedVariableRemaining()).isEqualByComparingTo(pace.paceProjection());
            assertThat(forecast.expectedVariableRemaining()).isEqualByComparingTo("7828.80");
            assertThat(forecast.pessimisticVariableRemaining()).isEqualByComparingTo("9786.00");   // × 1.25
            assertThat(forecast.optimisticVariableRemaining()).isEqualByComparingTo("5871.60");    // × 0.75
        }

        @Test
        void exactTypicalPessimisticAndOptimisticValues() {
            // d = 5, D = 25 → w = 0.8; median daily 100 × R 20 → paceProjection 2000
            CurrentPace pace = calculator.currentPace(daily("100.00", "100.00", "100.00", "100.00", "100.00"), 20);
            ForecastV2 forecast = calculator.forecast(input("5000.00", 5, 25, 20,
                    List.of(occurrence("500.00", LocalDate.of(2026, 9, 30), FixedOccurrenceBucket.LATER_THIS_CYCLE)),
                    pace, baseline(3, "1000.00", "800.00", "1300.00")));

            assertThat(forecast.historyWeight()).isEqualByComparingTo("0.8");
            assertThat(forecast.confidence()).isEqualTo(ForecastConfidence.HIGH);
            assertThat(forecast.expectedVariableRemaining()).isEqualByComparingTo("1200.00");     // 800 + 400
            assertThat(forecast.pessimisticVariableRemaining()).isEqualByComparingTo("1540.00");  // 1040 + 500
            assertThat(forecast.optimisticVariableRemaining()).isEqualByComparingTo("940.00");    // 640 + 300
            assertThat(forecast.committed()).isEqualByComparingTo("500.00");
            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("4500.00");
            assertThat(forecast.discretionaryPerDay()).isEqualByComparingTo("225.00");
            assertThat(forecast.expectedEndBalanceTypical()).isEqualByComparingTo("3300.00");
            assertThat(forecast.expectedEndBalanceLow()).isEqualByComparingTo("2960.00");        // pessimistic spend
            assertThat(forecast.expectedEndBalanceHigh()).isEqualByComparingTo("3560.00");       // optimistic spend
            assertThat(forecast.status()).isEqualTo(ForecastStatus.FINE);
        }

        @Test
        void pessimisticSpendYieldsTheLowBalanceAndTheInvariantHolds() {
            CurrentPace pace = calculator.currentPace(
                    daily("94.98", "335.64", "361.64", "316.76", "443.00", "0.00"), 24);
            for (HistoricalBaseline baseline : List.of(
                    noHistory(),
                    baseline(1, "2880.48", "2160.36", "3600.60"),
                    baseline(3, "2880.48", "2400.00", "3400.00"))) {
                ForecastV2 forecast = calculator.forecast(input("5896.89", 6, 30, 24, List.of(), pace, baseline));

                assertThat(forecast.pessimisticVariableRemaining()).isGreaterThanOrEqualTo(forecast.expectedVariableRemaining());
                assertThat(forecast.optimisticVariableRemaining()).isLessThanOrEqualTo(forecast.expectedVariableRemaining());
                assertThat(forecast.expectedEndBalanceLow()).isLessThanOrEqualTo(forecast.expectedEndBalanceTypical());
                assertThat(forecast.expectedEndBalanceTypical()).isLessThanOrEqualTo(forecast.expectedEndBalanceHigh());
                assertThat(forecast.expectedEndBalanceLow())
                        .isEqualByComparingTo(forecast.discretionaryNow().subtract(forecast.pessimisticVariableRemaining()));
                assertThat(forecast.expectedEndBalanceHigh())
                        .isEqualByComparingTo(forecast.discretionaryNow().subtract(forecast.optimisticVariableRemaining()));
            }
        }

        @Test
        void malformedBaselineWithPositiveHistoryButNoMedianFailsFast() {
            // HistoricalBaseline.of never produces cyclesUsed > 0 with a null median, but forecast() must not
            // silently misprice the blend if a caller ever hands it one — it should fail loudly instead.
            CurrentPace pace = calculator.currentPace(daily("10.00"), 5);
            HistoricalBaseline malformed = new HistoricalBaseline(1, null, null, null, List.of(), null);

            assertThatThrownBy(() -> calculator.forecast(input("100.00", 1, 10, 5, List.of(), pace, malformed)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void confidenceFollowsCyclesUsed() {
            CurrentPace pace = calculator.currentPace(daily("10.00"), 29);
            assertThat(calculator.forecast(input("100.00", 1, 30, 29, List.of(), pace, noHistory())).confidence())
                    .isEqualTo(ForecastConfidence.LOW);
            assertThat(calculator.forecast(input("100.00", 1, 30, 29, List.of(), pace, baseline(1, "10.00", "7.50", "12.50"))).confidence())
                    .isEqualTo(ForecastConfidence.MEDIUM);
            assertThat(calculator.forecast(input("100.00", 1, 30, 29, List.of(), pace, baseline(2, "10.00", "7.50", "12.50"))).confidence())
                    .isEqualTo(ForecastConfidence.MEDIUM);
            assertThat(calculator.forecast(input("100.00", 1, 30, 29, List.of(), pace, baseline(3, "10.00", "9.00", "11.00"))).confidence())
                    .isEqualTo(ForecastConfidence.HIGH);
        }
    }

    @Nested
    class Status {

        private final CurrentPace fixturePace = calculator.currentPace(
                daily("94.98", "335.64", "361.64", "316.76", "443.00", "0.00"), 24);
        private final HistoricalBaseline fixtureBaseline = baseline(3, "2880.48", "2400.00", "3400.00");

        @Test
        void negativeDiscretionaryIsShort() {
            ForecastV2 forecast = calculator.forecast(input("400.00", 6, 30, 24,
                    List.of(occurrence("500.00", LocalDate.of(2026, 9, 20), FixedOccurrenceBucket.DUE_SOON)),
                    fixturePace, fixtureBaseline));

            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("-100.00");
            assertThat(forecast.status()).isEqualTo(ForecastStatus.SHORT);
        }

        @Test
        void uncoverableOccurrenceOnThePessimisticPathIsShort() {
            // 4,300 due on cycle day 28 (Oct 6, 22 days out): 5000 − 4300 − 4677.20 × 22/24 (= 4287.43) < 0
            ForecastV2 forecast = calculator.forecast(input("5000.00", 6, 30, 24,
                    List.of(occurrence("4300.00", LocalDate.of(2026, 10, 6), FixedOccurrenceBucket.LATER_THIS_CYCLE)),
                    fixturePace, fixtureBaseline));

            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("700.00");         // not negative
            assertThat(forecast.pessimisticVariableRemaining()).isEqualByComparingTo("4677.20");
            assertThat(forecast.status()).isEqualTo(ForecastStatus.SHORT);
        }

        @Test
        void occurrencesAreCheckedCumulativelyByDueDate() {
            // pace 10 × 20 = 200; pessimistic = 0.8 × 200 + 0.2 × 200 × 1.25 = 210 → 105 prorated to day 10.
            // The later occurrence alone is coverable (700 − 300 − 105 ≥ 0); after the earlier one it is not
            // (700 − 300 − 300 − 105 < 0) although discretionaryNow stays positive.
            CurrentPace pace = calculator.currentPace(daily("10.00", "10.00", "10.00", "10.00", "10.00"), 20);
            HistoricalBaseline baseline = baseline(3, "200.00", "160.00", "200.00");
            List<CommittedOccurrence> occurrences = List.of(
                    occurrence("300.00", TODAY.plusDays(10), FixedOccurrenceBucket.LATER_THIS_CYCLE),
                    occurrence("300.00", TODAY.plusDays(2), FixedOccurrenceBucket.DUE_SOON));

            ForecastV2 forecast = calculator.forecast(input("700.00", 5, 25, 20, occurrences, pace, baseline));

            assertThat(forecast.pessimisticVariableRemaining()).isEqualByComparingTo("210.00");
            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("100.00");
            assertThat(forecast.status()).isEqualTo(ForecastStatus.SHORT);
            assertThat(calculator.forecast(input("700.00", 5, 25, 20, occurrences.subList(0, 1), pace, baseline)).status())
                    .isNotEqualTo(ForecastStatus.SHORT);
        }

        @Test
        void multipleOccurrencesDueOnTheSameDaySumBeforeBeingCheckedRegardlessOfOrder() {
            // Two occurrences share a due date 20 days out (= R): pace 10 x 5 days -> median 10, projection 200;
            // w = 0.8 (d=5, D=25), pessimistic = 0.8*200 + 0.2*200*1.25 = 210, fully prorated at daysUntil = R.
            // The first 300 alone is coverable (700 - 300 - 210 = 190 >= 0); the second is not once summed
            // (700 - 600 - 210 = -110 < 0) - and that must hold no matter which of the two is listed first.
            CurrentPace pace = calculator.currentPace(daily("10.00", "10.00", "10.00", "10.00", "10.00"), 20);
            HistoricalBaseline baseline = baseline(3, "200.00", "160.00", "200.00");
            LocalDate sameDueDate = TODAY.plusDays(20);
            CommittedOccurrence a = occurrence("300.00", sameDueDate, FixedOccurrenceBucket.LATER_THIS_CYCLE);
            CommittedOccurrence b = occurrence("300.00", sameDueDate, FixedOccurrenceBucket.LATER_THIS_CYCLE);

            ForecastV2 forwardOrder = calculator.forecast(input("700.00", 5, 25, 20, List.of(a, b), pace, baseline));
            ForecastV2 reverseOrder = calculator.forecast(input("700.00", 5, 25, 20, List.of(b, a), pace, baseline));

            assertThat(forwardOrder.pessimisticVariableRemaining()).isEqualByComparingTo("210.00");
            assertThat(forwardOrder.committed()).isEqualByComparingTo("600.00");
            assertThat(forwardOrder.discretionaryNow()).isEqualByComparingTo("100.00");   // stays positive
            assertThat(forwardOrder.status()).isEqualTo(ForecastStatus.SHORT);
            assertThat(reverseOrder.status()).isEqualTo(ForecastStatus.SHORT);
        }

        @Test
        void expectedVariableAboveDiscretionaryIsTight() {
            // discretionary 2900 < expected 3870.14; the 100 due in two days is covered: 3000 − 100 − 389.77 > 0
            ForecastV2 forecast = calculator.forecast(input("3000.00", 6, 30, 24,
                    List.of(occurrence("100.00", LocalDate.of(2026, 9, 16), FixedOccurrenceBucket.DUE_SOON)),
                    fixturePace, fixtureBaseline));

            assertThat(forecast.expectedVariableRemaining()).isEqualByComparingTo("3870.14");
            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("2900.00");
            assertThat(forecast.status()).isEqualTo(ForecastStatus.TIGHT);
        }

        @Test
        void otherwiseFine() {
            ForecastV2 forecast = calculator.forecast(input("5896.89", 6, 30, 24, List.of(), fixturePace, fixtureBaseline));

            assertThat(forecast.status()).isEqualTo(ForecastStatus.FINE);
        }

        @Test
        void anOccurrenceDueTodayGetsNoProratedVariableSpend() {
            // zero history, median 392 × R 20 → pace 7840, pessimistic 9800 → 490 per day
            CurrentPace pace = calculator.currentPace(daily("392.00", "392.00", "392.00", "392.00", "392.00", "392.00"), 20);
            CommittedOccurrence dueToday = occurrence("600.00", TODAY, FixedOccurrenceBucket.DUE_SOON);
            CommittedOccurrence dueTomorrow = occurrence("600.00", TODAY.plusDays(1), FixedOccurrenceBucket.DUE_SOON);

            ForecastV2 today = calculator.forecast(input("1000.00", 6, 26, 20, List.of(dueToday), pace, noHistory()));
            ForecastV2 tomorrow = calculator.forecast(input("1000.00", 6, 26, 20, List.of(dueTomorrow), pace, noHistory()));

            assertThat(today.pessimisticVariableRemaining()).isEqualByComparingTo("9800.00");
            assertThat(today.status()).isEqualTo(ForecastStatus.TIGHT);      // 1000 − 600 − 0 ≥ 0, expected > 400
            assertThat(tomorrow.status()).isEqualTo(ForecastStatus.SHORT);   // 1000 − 600 − 490 < 0
        }

        @Test
        void anOverdueOccurrenceIsTreatedLikeDueToday() {
            CurrentPace pace = calculator.currentPace(daily("392.00", "392.00", "392.00", "392.00", "392.00", "392.00"), 20);
            CommittedOccurrence overdue = occurrence("600.00", TODAY.minusDays(3), FixedOccurrenceBucket.OVERDUE);

            ForecastV2 forecast = calculator.forecast(input("1000.00", 6, 26, 20, List.of(overdue), pace, noHistory()));

            assertThat(forecast.committed()).isEqualByComparingTo("600.00");
            assertThat(forecast.status()).isEqualTo(ForecastStatus.TIGHT);
        }

        @Test
        void lastDayOfTheCycleWithNoRemainingHorizonIsSafe() {
            // three-day cycle viewed on its last day: d = D = 3, R = 0 → pace projection 0, every normalised
            // contribution 0, nothing to prorate and no division anywhere
            LocalDate lastDay = START.plusDays(2);
            CurrentPace pace = calculator.currentPace(daily("100.00", "50.00", "0.00"), 0);
            HistoricalBaseline baseline = new HistoricalBaseline(3, money("0.00"), money("0.00"), money("0.00"), List.of(), null);
            List<CommittedOccurrence> occurrences = List.of(
                    occurrence("100.00", lastDay, FixedOccurrenceBucket.DUE_SOON),
                    occurrence("50.00", lastDay.minusDays(1), FixedOccurrenceBucket.OVERDUE));

            ForecastV2 forecast = calculator.forecast(new ForecastInput(money("1000.00"), lastDay, 3, 3, 0,
                    occurrences, pace, baseline));

            assertThat(forecast.historyWeight()).isEqualByComparingTo("0.3");
            assertThat(forecast.expectedVariableRemaining()).isEqualByComparingTo("0.00");
            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("850.00");
            assertThat(forecast.discretionaryPerDay()).isEqualByComparingTo("850.00");   // / max(R, 1)
            assertThat(forecast.expectedEndBalanceTypical()).isEqualByComparingTo("850.00");
            assertThat(forecast.status()).isEqualTo(ForecastStatus.FINE);

            ForecastV2 shortOne = calculator.forecast(new ForecastInput(money("100.00"), lastDay, 3, 3, 0,
                    occurrences, pace, baseline));
            assertThat(shortOne.status()).isEqualTo(ForecastStatus.SHORT);
        }

        @Test
        void afterPaydayAndPaidRowsNeverTouchCommittedOrStatus() {
            ForecastInput without = input("5896.89", 6, 30, 24, List.of(), fixturePace, fixtureBaseline);
            ForecastInput with = input("5896.89", 6, 30, 24, List.of(
                    occurrence("100000.00", LocalDate.of(2026, 10, 9), FixedOccurrenceBucket.AFTER_PAYDAY),
                    occurrence("100000.00", LocalDate.of(2026, 9, 10), FixedOccurrenceBucket.PAID_THIS_CYCLE)),
                    fixturePace, fixtureBaseline);

            assertThat(calculator.forecast(with)).isEqualTo(calculator.forecast(without));
            assertThat(calculator.forecast(with).committed()).isEqualByComparingTo("0.00");
            assertThat(calculator.forecast(with).status()).isEqualTo(ForecastStatus.FINE);

            // even next to a genuine commitment the AFTER_PAYDAY row changes nothing
            CommittedOccurrence real = occurrence("500.00", LocalDate.of(2026, 9, 30), FixedOccurrenceBucket.LATER_THIS_CYCLE);
            ForecastV2 realOnly = calculator.forecast(input("5896.89", 6, 30, 24, List.of(real), fixturePace, fixtureBaseline));
            ForecastV2 realPlusAfter = calculator.forecast(input("5896.89", 6, 30, 24, List.of(real,
                    occurrence("100000.00", LocalDate.of(2026, 10, 9), FixedOccurrenceBucket.AFTER_PAYDAY)),
                    fixturePace, fixtureBaseline));
            assertThat(realPlusAfter).isEqualTo(realOnly);
            assertThat(realPlusAfter.committed()).isEqualByComparingTo("500.00");
        }

        @Test
        void aDayIndexThatDisagreesWithTheSeriesLengthIsRejected() {
            // the fixture series has six days (Sep 9–14) → d must be 6; a 0-based or stale d would shift the weight silently
            assertThatThrownBy(() -> calculator.forecast(input("5896.89", 5, 30, 24, List.of(), fixturePace, fixtureBaseline)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dayIndex 5");
            assertThatThrownBy(() -> calculator.forecast(input("5896.89", 7, 30, 24, List.of(), fixturePace, fixtureBaseline)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void anOccurrenceWithoutABucketIsRejected() {
            assertThatThrownBy(() -> new CommittedOccurrence(TODAY, money("1.00"), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class September14Fixture {

        @Test
        void matchesThePlannedNumbersAndIsFine() {
            CurrentPace pace = calculator.currentPace(
                    daily(September2026Fixture.DAILY_VARIABLE_SEP_9_TO_14.stream().map(BigDecimal::toPlainString).toArray(String[]::new)),
                    24);
            List<CommittedOccurrence> occurrences = new ArrayList<>();
            September2026Fixture.PENDING_OCCURRENCES.forEach(o -> occurrences.add(new CommittedOccurrence(o.dueDate(),
                    o.plannedAmount(), FixedOccurrenceBucket.of(OccurrenceStatus.PENDING, o.dueDate(), TODAY, CYCLE_END))));
            September2026Fixture.PAID_OCCURRENCES.forEach(o -> occurrences.add(new CommittedOccurrence(o.dueDate(),
                    o.plannedAmount(), FixedOccurrenceBucket.of(OccurrenceStatus.PAID, o.dueDate(), TODAY, CYCLE_END))));
            // history median remaining ≈ 120.02/day × 24 (plan); p25/p75 synthetic around it
            HistoricalBaseline baseline = baseline(3, "2880.48", "2400.00", "3400.00");

            ForecastV2 forecast = calculator.forecast(new ForecastInput(September2026Fixture.SALARY_WALLET_BALANCE,
                    TODAY, 6, 30, 24, occurrences, pace, baseline));

            assertThat(forecast.committed()).isEqualByComparingTo(September2026Fixture.REMAINING_FIXED_PAY_CYCLE);
            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("5102.62");
            assertThat(forecast.discretionaryPerDay()).isEqualByComparingTo("212.61");
            assertThat(forecast.historyWeight()).isEqualByComparingTo("0.80");
            assertThat(forecast.confidence()).isEqualTo(ForecastConfidence.HIGH);
            assertThat(pace.trimmedDailyPace()).isEqualByComparingTo("326.20");
            assertThat(forecast.expectedVariableRemaining()).isEqualByComparingTo("3870.14");   // ≈ 3,870 (plan)
            assertThat(forecast.expectedEndBalanceTypical()).isEqualByComparingTo("1232.48");
            assertThat(forecast.expectedEndBalanceTypical()).isPositive()
                    .isBetween(new BigDecimal("800.00"), new BigDecimal("2800.00"));
            assertThat(forecast.expectedEndBalanceLow()).isEqualByComparingTo("425.42");         // 5102.62 − 4677.20
            assertThat(forecast.expectedEndBalanceHigh()).isEqualByComparingTo("2008.30");       // 5102.62 − 3094.32
            assertThat(forecast.status()).isEqualTo(ForecastStatus.FINE);
        }
    }

    // ---------------------------------------------------------------------------------------------

    static ForecastInput input(String walletBalance, int dayIndex, int cycleLengthDays, int daysRemaining,
                               List<CommittedOccurrence> occurrences, CurrentPace pace, HistoricalBaseline baseline) {
        return new ForecastInput(money(walletBalance), TODAY, dayIndex, cycleLengthDays, daysRemaining, occurrences,
                pace, baseline);
    }

    static CommittedOccurrence occurrence(String amount, LocalDate dueDate, FixedOccurrenceBucket bucket) {
        return new CommittedOccurrence(dueDate, money(amount), bucket);
    }

    static HistoricalBaseline noHistory() {
        return new HistoricalBaseline(0, null, null, null, List.of(), null);
    }

    static HistoricalBaseline baseline(int cyclesUsed, String median, String p25, String p75) {
        return new HistoricalBaseline(cyclesUsed, money(median), money(p25), money(p75), List.of(), null);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private static List<DailyTotal> daily(String... amounts) {
        List<DailyTotal> totals = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            totals.add(new DailyTotal(START.plusDays(i), new BigDecimal(amounts[i])));
        }
        return totals;
    }
}
