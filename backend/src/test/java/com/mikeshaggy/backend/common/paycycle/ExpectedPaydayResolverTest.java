package com.mikeshaggy.backend.common.paycycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikeshaggy.backend.regression.September2026Fixture;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpectedPaydayResolverTest {

    private static final UUID USER_ID = September2026Fixture.USER_ID;

    /** The 11 audited anchors, oldest first. */
    private static final List<LocalDate> ANCHORS_ASC = September2026Fixture.ANCHOR_DATES;

    private final ExpectedPaydayResolver resolver = new ExpectedPaydayResolver();

    /** Anchors strictly before index {@code exclusiveEnd}, newest first. */
    private static List<LocalDate> historyDescBefore(int exclusiveEnd) {
        List<LocalDate> desc = new ArrayList<>(ANCHORS_ASC.subList(0, exclusiveEnd));
        Collections.reverse(desc);
        return desc;
    }

    @Nested
    @DisplayName("Backtest over the 11 fixture anchors")
    class Backtest {

        static Stream<Arguments> completedCycles() {
            return IntStream.range(1, ANCHORS_ASC.size())
                    .mapToObj(i -> Arguments.of(i, ANCHORS_ASC.get(i - 1), ANCHORS_ASC.get(i)));
        }

        @ParameterizedTest(name = "anchor #{0}: after {1} the salary arrived {2}")
        @MethodSource("completedCycles")
        void predictionIsNeverLateAndAtMostOneDayEarly(int index, LocalDate lastAnchor, LocalDate actual) {
            ExpectedPayday predicted = resolver.resolve(USER_ID, lastAnchor, historyDescBefore(index));

            long salaryArrivedDaysBeforePrediction = ChronoUnit.DAYS.between(actual, predicted.date());
            // "late" = the prediction lands before the salary (degenerate days); never allowed
            assertThat(predicted.date()).as("prediction after %s", lastAnchor).isAfterOrEqualTo(actual);
            // the salary may arrive at most one day before the prediction (Sep 9 vs predicted Sep 10)
            assertThat(salaryArrivedDaysBeforePrediction).isBetween(0L, 1L);
        }

        @Test
        void exactHitsAndTheSingleEarlyArrival() {
            List<LocalDate> predictions = IntStream.range(1, ANCHORS_ASC.size())
                    .mapToObj(i -> resolver.resolve(USER_ID, ANCHORS_ASC.get(i - 1), historyDescBefore(i)).date())
                    .toList();

            assertThat(predictions).containsExactly(
                    LocalDate.of(2025, 12, 10), // +1 month (1 anchor)
                    LocalDate.of(2026, 1, 9),   // median length 30 (2 anchors)
                    LocalDate.of(2026, 2, 10),  // learned from here on
                    LocalDate.of(2026, 3, 10),
                    LocalDate.of(2026, 4, 10),
                    LocalDate.of(2026, 5, 8),   // May 10 is a Sunday → Friday May 8
                    LocalDate.of(2026, 6, 10),
                    LocalDate.of(2026, 7, 10),
                    LocalDate.of(2026, 8, 10),
                    LocalDate.of(2026, 9, 10)); // actual Sep 9 arrived one day early (harmless)
        }

        @Test
        void learnedRuleIsTenthOfMonthShiftedToPreviousBusinessDay() {
            ExpectedPayday next = resolver.resolve(USER_ID, September2026Fixture.CURRENT_CYCLE_START,
                    historyDescBefore(ANCHORS_ASC.size()));

            assertThat(next.ruleUsed()).isEqualTo(
                    new PaydayRule(10, WeekendShift.PREVIOUS_BUSINESS_DAY, PaydayRuleSource.LEARNED));
        }

        @Test
        void nextPaydayAfterSeptember9IsOctober9() {
            ExpectedPayday next = resolver.resolve(USER_ID, September2026Fixture.CURRENT_CYCLE_START,
                    historyDescBefore(ANCHORS_ASC.size()));

            // Oct 10 2026 is a Saturday
            assertThat(next.date()).isEqualTo(LocalDate.of(2026, 10, 9));
        }

        @Test
        void historyOrderAndDuplicatesDoNotMatter() {
            List<LocalDate> shuffledWithDuplicates = new ArrayList<>(ANCHORS_ASC);
            shuffledWithDuplicates.addAll(ANCHORS_ASC);
            Collections.shuffle(shuffledWithDuplicates, new java.util.Random(42));

            ExpectedPayday next = resolver.resolve(USER_ID, September2026Fixture.CURRENT_CYCLE_START,
                    shuffledWithDuplicates);

            assertThat(next.date()).isEqualTo(LocalDate.of(2026, 10, 9));
            assertThat(next.ruleUsed().source()).isEqualTo(PaydayRuleSource.LEARNED);
        }
    }

    @Nested
    class RuleOrder {

        @Test
        void configuredRuleOverridesLearning() {
            PaydayRule configured = PaydayRule.configured(25, WeekendShift.NEXT_BUSINESS_DAY);

            ExpectedPayday next = resolver.resolve(USER_ID, September2026Fixture.CURRENT_CYCLE_START,
                    historyDescBefore(ANCHORS_ASC.size()), configured);

            // Oct 25 2026 is a Sunday → Monday Oct 26
            assertThat(next.date()).isEqualTo(LocalDate.of(2026, 10, 26));
            assertThat(next.ruleUsed()).isEqualTo(configured);
            assertThat(next.ruleUsed().source()).isEqualTo(PaydayRuleSource.CONFIGURED);
        }

        @Test
        void configuredRuleSourceIsForcedToConfigured() {
            PaydayRule mislabelled = new PaydayRule(10, WeekendShift.NONE, PaydayRuleSource.LEARNED);

            ExpectedPayday next = resolver.resolve(USER_ID, LocalDate.of(2026, 9, 9), List.of(), mislabelled);

            assertThat(next.ruleUsed().source()).isEqualTo(PaydayRuleSource.CONFIGURED);
            assertThat(next.date()).isEqualTo(LocalDate.of(2026, 10, 10));
        }

        @Test
        void medianLengthFallbackWithTwoAnchors() {
            LocalDate lastAnchor = LocalDate.of(2025, 12, 10);

            ExpectedPayday next = resolver.resolve(USER_ID, lastAnchor,
                    List.of(lastAnchor, LocalDate.of(2025, 11, 10)));

            // 30-day cycle → Jan 9, not Jan 10 (+1 month)
            assertThat(next.date()).isEqualTo(LocalDate.of(2026, 1, 9));
            assertThat(next.ruleUsed().source()).isEqualTo(PaydayRuleSource.MEDIAN_LENGTH);
        }

        @Test
        void plusOneMonthWithSingleAnchor() {
            LocalDate lastAnchor = LocalDate.of(2025, 11, 10);

            ExpectedPayday next = resolver.resolve(USER_ID, lastAnchor, List.of(lastAnchor));

            assertThat(next.date()).isEqualTo(LocalDate.of(2025, 12, 10));
            assertThat(next.ruleUsed().source()).isEqualTo(PaydayRuleSource.PLUS_ONE_MONTH);
        }

        @Test
        void plusOneMonthWhenHistoryIsMissing() {
            ExpectedPayday next = resolver.resolve(USER_ID, LocalDate.of(2026, 1, 31), null);

            assertThat(next.date()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(next.ruleUsed().source()).isEqualTo(PaydayRuleSource.PLUS_ONE_MONTH);
        }

        @Test
        void learnedRuleWithoutWeekendEvidenceDoesNotShift() {
            // 15th of the month; every anchor and every 15th in these months is a weekday, so nothing to learn about shifts
            List<LocalDate> anchorsDesc = List.of(
                    LocalDate.of(2026, 4, 15),  // Wed
                    LocalDate.of(2026, 1, 15),  // Thu
                    LocalDate.of(2025, 12, 15)); // Mon

            ExpectedPayday next = resolver.resolve(USER_ID, LocalDate.of(2026, 4, 15), anchorsDesc);

            assertThat(next.ruleUsed()).isEqualTo(new PaydayRule(15, WeekendShift.NONE, PaydayRuleSource.LEARNED));
            assertThat(next.date()).isEqualTo(LocalDate.of(2026, 5, 15)); // Friday
        }
    }

    @Nested
    class DayOfMonthClamping {

        @Test
        void thirtyFirstClampsToThirtyDayMonth() {
            PaydayRule configured = PaydayRule.configured(31, WeekendShift.NONE);

            ExpectedPayday next = resolver.resolve(USER_ID, LocalDate.of(2026, 3, 31), List.of(), configured);

            assertThat(next.date()).isEqualTo(LocalDate.of(2026, 4, 30));
        }

        @Test
        void thirtyFirstClampsToFebruary() {
            PaydayRule configured = PaydayRule.configured(31, WeekendShift.NONE);

            ExpectedPayday next = resolver.resolve(USER_ID, LocalDate.of(2026, 1, 31), List.of(), configured);

            assertThat(next.date()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        void candidateMustBeStrictlyAfterMinimumCycleLength() {
            // Configured 5th; last anchor Sep 5 → Sep 25 + minCycleDays means Oct 5 is the first valid occurrence
            PaydayRule configured = PaydayRule.configured(5, WeekendShift.NONE);

            ExpectedPayday next = resolver.resolve(USER_ID, LocalDate.of(2026, 9, 5), List.of(), configured);

            assertThat(next.date()).isEqualTo(LocalDate.of(2026, 10, 5));
        }
    }
}
