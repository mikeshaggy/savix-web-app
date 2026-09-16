package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CommittedOccurrence;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.ForecastInput;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.ForecastV2;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.HistoricalBaseline;
import com.mikeshaggy.backend.analytics.forecast.OneOffDetector.OneOff;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.VariableExpense;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import com.mikeshaggy.backend.regression.September2026Fixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stage 4.6 — thresholds, day rule, deduplication, exclusion semantics and forecast impact. Synthetic data only. */
class OneOffDetectorTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    private final ForecastV2Calculator calculator = new ForecastV2Calculator();
    private final OneOffDetector detector = new OneOffDetector(calculator);

    // --- transaction rule -----------------------------------------------------------------------

    @Test
    void exactlyTwoHundredQualifiesWhenFifteenPercentIsLower() {
        // V = 700 → 15 % = 105 → threshold 200; day rule 280 never reached
        List<VariableExpense> candidates = List.of(
                tx(1, START, "100.00"), tx(2, START.plusDays(1), "100.00"), tx(3, START.plusDays(2), "100.00"),
                tx(4, START.plusDays(3), "100.00"), tx(5, START.plusDays(4), "100.00"),
                tx(6, START.plusDays(5), "200.00"));

        List<OneOff> oneOffs = detector.detect(candidates, dailyOf(candidates), zeroHistoryInput(24));

        assertThat(oneOffs).extracting(OneOff::transactionId).containsExactly(6L);
        assertThat(oneOffs.getFirst().shareOfVariable()).isEqualByComparingTo("28.57");
    }

    @Test
    void exactlyFifteenPercentQualifiesWhenItExceedsTwoHundred() {
        // V = 2000 → 15 % = 300.00 exactly; day totals 340 < 800
        List<VariableExpense> candidates = List.of(
                tx(1, START, "340.00"), tx(2, START.plusDays(1), "340.00"), tx(3, START.plusDays(2), "340.00"),
                tx(4, START.plusDays(3), "340.00"), tx(5, START.plusDays(4), "340.00"),
                tx(6, START.plusDays(5), "300.00"));

        List<OneOff> oneOffs = detector.detect(candidates, dailyOf(candidates), zeroHistoryInput(24));

        assertThat(oneOffs).extracting(OneOff::transactionId).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(oneOffs.getLast().transactionId()).isEqualTo(6L);    // 300.00 at exactly 15 % — in, and last
        assertThat(oneOffs).extracting(OneOff::amount).allMatch(a -> a.compareTo(new BigDecimal("300.00")) >= 0);
    }

    @Test
    void belowBothThresholdsDoesNotQualify() {
        // V = 699.99 → threshold 200; 199.99 misses it. V = 2000 → threshold 300; 299.99 misses it.
        List<VariableExpense> low = List.of(
                tx(1, START, "100.00"), tx(2, START.plusDays(1), "100.00"), tx(3, START.plusDays(2), "100.00"),
                tx(4, START.plusDays(3), "100.00"), tx(5, START.plusDays(4), "100.00"),
                tx(6, START.plusDays(5), "199.99"));
        List<VariableExpense> high = List.of(
                tx(1, START, "340.01"), tx(2, START.plusDays(1), "340.00"), tx(3, START.plusDays(2), "340.00"),
                tx(4, START.plusDays(3), "340.00"), tx(5, START.plusDays(4), "340.00"),
                tx(6, START.plusDays(5), "299.99"));

        assertThat(detector.detect(low, dailyOf(low), zeroHistoryInput(24))).isEmpty();
        assertThat(detector.detect(high, dailyOf(high), zeroHistoryInput(24)))
                .extracting(OneOff::transactionId).doesNotContain(6L);
    }

    // --- day rule -------------------------------------------------------------------------------

    @Test
    void exactlyFortyPercentDayTotalQualifiesAndReportsTheLargestTransactionOfThatDay() {
        // V = 1000 → 15 % = 150 (no single row reaches it), 40 % = 400 = Sep 12 total (140 + 130 + 130)
        List<VariableExpense> candidates = List.of(
                tx(1, START, "120.00"), tx(2, START.plusDays(1), "120.00"), tx(3, START.plusDays(2), "120.00"),
                tx(4, START.plusDays(3), "130.00"), tx(5, START.plusDays(3), "140.00"), tx(6, START.plusDays(3), "130.00"),
                tx(7, START.plusDays(4), "120.00"), tx(8, START.plusDays(5), "120.00"));

        List<OneOff> oneOffs = detector.detect(candidates, dailyOf(candidates), zeroHistoryInput(24));

        assertThat(oneOffs).hasSize(1);
        assertThat(oneOffs.getFirst().transactionId()).isEqualTo(5L);
        assertThat(oneOffs.getFirst().amount()).isEqualByComparingTo("140.00");
        assertThat(oneOffs.getFirst().shareOfVariable()).isEqualByComparingTo("14.00");
    }

    @Test
    void dayJustBelowFortyPercentDoesNotQualify() {
        // V = 1000.01 → 40 % = 400.004 > 400
        List<VariableExpense> candidates = List.of(
                tx(1, START, "120.01"), tx(2, START.plusDays(1), "120.00"), tx(3, START.plusDays(2), "120.00"),
                tx(4, START.plusDays(3), "130.00"), tx(5, START.plusDays(3), "140.00"), tx(6, START.plusDays(3), "130.00"),
                tx(7, START.plusDays(4), "120.00"), tx(8, START.plusDays(5), "120.00"));

        assertThat(detector.detect(candidates, dailyOf(candidates), zeroHistoryInput(24))).isEmpty();
    }

    @Test
    void aTransactionMatchingBothRulesIsReturnedOnce() {
        // V = 700 → transaction threshold 200 (300 ≥), day threshold 280 (300 ≥) — same row, once
        List<VariableExpense> candidates = List.of(
                tx(1, START, "100.00"), tx(2, START.plusDays(1), "100.00"), tx(3, START.plusDays(2), "100.00"),
                tx(4, START.plusDays(3), "100.00"), tx(5, START.plusDays(5), "300.00"));

        List<OneOff> oneOffs = detector.detect(candidates, dailyOf(candidates), zeroHistoryInput(24));

        assertThat(oneOffs).extracting(OneOff::transactionId).containsExactly(5L);
    }

    @Test
    void resultsAreOrderedByAmountDescending() {
        // V = 1000 → threshold 200: 250, 210 and 500 qualify; day threshold 400 — Sep 12 (500) too, deduplicated
        List<VariableExpense> candidates = List.of(
                tx(1, START, "210.00"), tx(2, START.plusDays(1), "250.00"), tx(3, START.plusDays(3), "500.00"),
                tx(4, START.plusDays(4), "40.00"));

        List<OneOff> oneOffs = detector.detect(candidates, dailyOf(candidates), zeroHistoryInput(24));

        assertThat(oneOffs).extracting(OneOff::transactionId).containsExactly(3L, 2L, 1L);
    }

    @Test
    void tiedAmountsOnTheFlaggedDayPickTheLowerTransactionId() {
        // V = 1000 -> transaction threshold 200 (nothing here reaches it), day threshold 400 = Sep 12 total
        // (140 + 140 + 70 + 50). Both 140s tie for "largest that day" - pins the detector's deterministic
        // tie-break (amount desc, id asc) so a refactor cannot silently flip which transaction is reported.
        List<VariableExpense> candidates = List.of(
                tx(1, START, "120.00"), tx(2, START.plusDays(1), "120.00"), tx(3, START.plusDays(2), "120.00"),
                tx(100, START.plusDays(3), "140.00"), tx(50, START.plusDays(3), "140.00"),
                tx(6, START.plusDays(3), "70.00"), tx(7, START.plusDays(3), "50.00"),
                tx(8, START.plusDays(4), "120.00"), tx(9, START.plusDays(5), "120.00"));

        List<OneOff> oneOffs = detector.detect(candidates, dailyOf(candidates), zeroHistoryInput(24));

        assertThat(oneOffs).hasSize(1);
        assertThat(oneOffs.getFirst().transactionId()).isEqualTo(50L);
        assertThat(oneOffs.getFirst().amount()).isEqualByComparingTo("140.00");
        assertThat(oneOffs.getFirst().shareOfVariable()).isEqualByComparingTo("14.00");
    }

    @Test
    void noVariableSpendMeansNoOneOffs() {
        assertThat(detector.detect(List.of(), daily("0.00", "0.00"), zeroHistoryInput(24))).isEmpty();
        assertThat(detector.detect(List.of(tx(1, START, "0.00")), daily("0.00", "0.00"), zeroHistoryInput(24))).isEmpty();
    }

    // --- exclusion semantics ----------------------------------------------------------------------

    @Test
    void anAlreadyExcludedOneOffIsStillReportedWithExcludedTrueAndZeroImpact() {
        // The 300 is excluded from the pace: the series holds 143.00 on Sep 13, not 443.00.
        List<VariableExpense> candidates = fixtureCandidates(true);
        List<DailyTotal> daily = daily("94.98", "335.64", "361.64", "316.76", "143.00", "0.00");
        ForecastInput input = zeroHistoryInput(24);

        List<OneOff> oneOffs = detector.detect(candidates, daily, input);

        assertThat(oneOffs).hasSize(1);
        OneOff repayment = oneOffs.getFirst();
        assertThat(repayment.transactionId()).isEqualTo(300L);
        assertThat(repayment.title()).isEqualTo(September2026Fixture.ONE_OFF_TITLE);
        assertThat(repayment.categoryName()).isEqualTo(September2026Fixture.ONE_OFF_CATEGORY);
        assertThat(repayment.excluded()).isTrue();
        assertThat(repayment.shareOfVariable()).isEqualByComparingTo("19.33");          // share of all unlinked spend
        assertThat(repayment.impactOnExpectedVariableRemaining()).isEqualByComparingTo("0.00");
        assertThat(repayment.impactOnExpectedEndBalance()).isEqualByComparingTo("0.00");

        // pinned: the forecast with the excluded row is the forecast without it — it was never in the series
        ForecastV2 base = calculator.forecast(input.withCurrentPace(calculator.currentPace(daily, 24)));
        assertThat(base.currentPace().trimmedDailyPace()).isEqualByComparingTo("229.88");
        assertThat(base.expectedVariableRemaining()).isEqualByComparingTo("5517.12");
    }

    @Test
    void aCategoryExcludedOneOffIsReportedTheSameWay() {
        // the read model folds the category flag into `excluded`; the detector sees one boolean
        VariableExpense byCategory = new VariableExpense(300L, September2026Fixture.ONE_OFF_DATE,
                "Lent to a friend", "Money lent", new BigDecimal("300.00"), true);
        List<VariableExpense> candidates = new ArrayList<>(fixtureCandidates(false));
        candidates.set(candidates.size() - 1, byCategory);
        List<DailyTotal> daily = daily("94.98", "335.64", "361.64", "316.76", "143.00", "0.00");

        List<OneOff> oneOffs = detector.detect(candidates, daily, zeroHistoryInput(24));

        assertThat(oneOffs).hasSize(1);
        assertThat(oneOffs.getFirst().categoryName()).isEqualTo("Money lent");
        assertThat(oneOffs.getFirst().excluded()).isTrue();
        assertThat(oneOffs.getFirst().impactOnExpectedVariableRemaining()).isEqualByComparingTo("0.00");
        assertThat(oneOffs.getFirst().impactOnExpectedEndBalance()).isEqualByComparingTo("0.00");
    }

    // --- impact ---------------------------------------------------------------------------------

    @Test
    void removingANonExcludedOneOffLowersTheCurrentPaceAndRaisesTheEndBalance() {
        List<VariableExpense> candidates = fixtureCandidates(false);
        List<DailyTotal> daily = daily("94.98", "335.64", "361.64", "316.76", "443.00", "0.00");
        ForecastInput input = zeroHistoryInput(24);
        ForecastV2 base = calculator.forecast(input.withCurrentPace(calculator.currentPace(daily, 24)));

        List<OneOff> oneOffs = detector.detect(candidates, daily, input);

        assertThat(base.currentPace().trimmedDailyPace()).isEqualByComparingTo("326.20");
        assertThat(base.expectedVariableRemaining()).isEqualByComparingTo("7828.80");
        OneOff repayment = oneOffs.getFirst();
        assertThat(repayment.excluded()).isFalse();
        // without the 300 the Sep 13 total is 143.00 → median (143 + 316.76) / 2 = 229.88 → × 24 = 5517.12
        assertThat(repayment.impactOnExpectedVariableRemaining()).isEqualByComparingTo("2311.68");  // 7828.80 − 5517.12
        assertThat(repayment.impactOnExpectedEndBalance()).isEqualByComparingTo("2311.68");
        assertThat(repayment.impactOnExpectedEndBalance()).isPositive();
    }

    @Test
    void impactKeepsTheHistoricalBaselineAndCommittedFixed() {
        // 3 cycles, w = 0.8: only the (1 − w) pace term moves — 0.2 × (7828.80 − 5517.12) = 462.34 (rounded per forecast)
        List<VariableExpense> candidates = fixtureCandidates(false);
        List<DailyTotal> daily = daily("94.98", "335.64", "361.64", "316.76", "443.00", "0.00");
        ForecastInput input = input("5896.89", 24,
                List.of(new CommittedOccurrence(LocalDate.of(2026, 9, 20), new BigDecimal("794.27"), FixedOccurrenceBucket.DUE_SOON)),
                baseline(3, "2880.48", "2400.00", "3400.00"));

        OneOff repayment = detector.detect(candidates, daily, input).getFirst();

        assertThat(repayment.impactOnExpectedVariableRemaining()).isEqualByComparingTo("462.33");  // 3870.14 − 3407.81
        assertThat(repayment.impactOnExpectedEndBalance()).isEqualByComparingTo("462.33");
    }

    @Test
    void aCandidateOutsideTheSeriesIsRejected() {
        List<VariableExpense> candidates = List.of(tx(1, START.minusDays(1), "500.00"));

        assertThatThrownBy(() -> detector.detect(candidates, daily("0.00", "0.00"), zeroHistoryInput(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Sep 14 fixture ---------------------------------------------------------------------------

    @Test
    void september14SyntheticRepaymentIsDetectedWhenNotExcluded() {
        List<VariableExpense> candidates = fixtureCandidates(false);
        List<DailyTotal> daily = dailyOf(candidates);
        assertThat(daily).extracting(d -> d.amount().toPlainString())
                .containsExactly("94.98", "335.64", "361.64", "316.76", "443.00", "0.00");
        ForecastInput input = input(September2026Fixture.SALARY_WALLET_BALANCE.toPlainString(), 24,
                List.of(new CommittedOccurrence(LocalDate.of(2026, 10, 8), September2026Fixture.REMAINING_FIXED_PAY_CYCLE,
                        FixedOccurrenceBucket.LATER_THIS_CYCLE)),
                baseline(3, "2880.48", "2400.00", "3400.00"));

        List<OneOff> oneOffs = detector.detect(candidates, daily, input);

        assertThat(oneOffs).hasSize(1);
        OneOff repayment = oneOffs.getFirst();
        assertThat(repayment.amount()).isEqualByComparingTo(September2026Fixture.ONE_OFF_AMOUNT);
        assertThat(repayment.date()).isEqualTo(September2026Fixture.ONE_OFF_DATE);
        assertThat(repayment.title()).isEqualTo(September2026Fixture.ONE_OFF_TITLE);
        assertThat(repayment.categoryName()).isEqualTo(September2026Fixture.ONE_OFF_CATEGORY);
        assertThat(repayment.shareOfVariable()).isEqualByComparingTo("19.33");   // 300 / 1552.02 ≥ 15 %
        assertThat(repayment.excluded()).isFalse();
        assertThat(repayment.impactOnExpectedVariableRemaining()).isPositive();
        assertThat(repayment.impactOnExpectedEndBalance()).isPositive();
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * The Sep 9–14 fixture days as unlinked transactions (V = 1552.02): every day split so no ordinary row reaches
     * the 232.80 transaction threshold and no day reaches the 620.81 day threshold — only the 300 PLN repayment.
     */
    private static List<VariableExpense> fixtureCandidates(boolean repaymentExcluded) {
        return List.of(
                tx(1, START, "94.98"),
                tx(2, START.plusDays(1), "180.00"), tx(3, START.plusDays(1), "155.64"),
                tx(4, START.plusDays(2), "190.00"), tx(5, START.plusDays(2), "171.64"),
                tx(6, START.plusDays(3), "160.00"), tx(7, START.plusDays(3), "156.76"),
                tx(8, START.plusDays(4), "143.00"),
                new VariableExpense(300L, September2026Fixture.ONE_OFF_DATE, September2026Fixture.ONE_OFF_TITLE,
                        September2026Fixture.ONE_OFF_CATEGORY, September2026Fixture.ONE_OFF_AMOUNT, repaymentExcluded));
    }

    private static VariableExpense tx(long id, LocalDate date, String amount) {
        return new VariableExpense(id, date, "tx-" + id, "groceries", new BigDecimal(amount), false);
    }

    /** Zero-filled series START..TODAY from the non-excluded candidates — what the pace query would return. */
    private static List<DailyTotal> dailyOf(List<VariableExpense> candidates) {
        Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
        for (LocalDate day = START; !day.isAfter(TODAY); day = day.plusDays(1)) {
            byDay.put(day, BigDecimal.ZERO);
        }
        candidates.stream().filter(c -> !c.excluded())
                .forEach(c -> byDay.merge(c.date(), c.amount(), BigDecimal::add));
        return byDay.entrySet().stream().map(e -> new DailyTotal(e.getKey(), e.getValue().setScale(2))).toList();
    }

    private static List<DailyTotal> daily(String... amounts) {
        List<DailyTotal> totals = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            totals.add(new DailyTotal(START.plusDays(i), new BigDecimal(amounts[i])));
        }
        return totals;
    }

    private static ForecastInput zeroHistoryInput(int daysRemaining) {
        return input("5000.00", daysRemaining, List.of(), new HistoricalBaseline(0, null, null, null, List.of(), null));
    }

    private static ForecastInput input(String walletBalance, int daysRemaining, List<CommittedOccurrence> occurrences,
                                       HistoricalBaseline baseline) {
        // the current pace is a placeholder: the detector recomputes it from the daily series it is given
        ForecastV2Calculator.CurrentPace placeholder = new ForecastV2Calculator().currentPace(daily("0.00"), daysRemaining);
        return new ForecastInput(new BigDecimal(walletBalance), TODAY, 6, 30, daysRemaining, occurrences, placeholder, baseline);
    }

    private static HistoricalBaseline baseline(int cyclesUsed, String median, String p25, String p75) {
        return new HistoricalBaseline(cyclesUsed, new BigDecimal(median), new BigDecimal(p25), new BigDecimal(p75), List.of(), null);
    }
}
