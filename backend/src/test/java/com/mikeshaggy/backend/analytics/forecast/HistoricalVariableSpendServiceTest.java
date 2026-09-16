package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleContribution;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.HistoricalBaseline;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Stage 4.3 — six synthetic closed cycles of lengths 28..33 (Mar 10 → Sep 8), current cycle Sep 9 → Oct 8,
 * evaluated on Sep 14: {@code d = 6}, {@code R = 24}. Hand-computed (spend before and on day 6 is ignored):
 * <pre>
 * cycle  L   remaining  days  normalised (× 24 / days)
 * C1     28   1100.00    22   1200.00
 * C2     29   1381.00    23   1441.04   (33144 / 23 = 1441.0434…)
 * C3     30   1500.00    24   1500.00
 * C4     31   1250.00    25   1200.00
 * C5     32   1690.00    26   1560.00
 * C6     33   2700.00    27   2400.00
 * sorted 1200, 1200, 1441.04, 1500, 1560, 2400 → median 1470.52, p25 1260.26, p75 1545.00, /24 = 61.27
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
class HistoricalVariableSpendServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Integer WALLET = 7;
    private static final int D = 6;
    private static final int R = 24;

    private static final PayCycle CURRENT = PayCycle.open(USER, WALLET, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9));
    private static final PayCycle C1 = closed(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 6));   // 28
    private static final PayCycle C2 = closed(LocalDate.of(2026, 4, 7), LocalDate.of(2026, 5, 5));    // 29
    private static final PayCycle C3 = closed(LocalDate.of(2026, 5, 6), LocalDate.of(2026, 6, 4));    // 30
    private static final PayCycle C4 = closed(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 7, 5));    // 31
    private static final PayCycle C5 = closed(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 8, 6));    // 32
    private static final PayCycle C6 = closed(LocalDate.of(2026, 8, 7), LocalDate.of(2026, 9, 8));    // 33

    @Mock
    private PayCycleService payCycleService;
    @Mock
    private AnalyticsTransactionQueryService queryService;
    @InjectMocks
    private HistoricalVariableSpendService service;

    @Test
    void sixCyclesYieldHandComputedNormalisedContributionsAndQuantiles() {
        assertThat(List.of(C1, C2, C3, C4, C5, C6)).extracting(PayCycle::lengthDays).containsExactly(28, 29, 30, 31, 32, 33);
        when(payCycleService.history(USER, 6)).thenReturn(List.of(C6, C5, C4, C3, C2, C1));
        stubSeries(C1, Map.of(1, "999.00", 6, "50.00", 7, "600.00", 28, "500.00"));
        stubSeries(C2, Map.of(3, "200.00", 29, "1381.00"));
        stubSeries(C3, constantFrom(C3, 7, "62.50"));
        stubSeries(C4, Map.of(6, "77.00", 10, "1250.00"));
        stubSeries(C5, Map.of(7, "1000.00", 32, "690.00"));
        stubSeries(C6, constantFrom(C6, 7, "100.00"));

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, D, R);

        assertThat(baseline.cyclesUsed()).isEqualTo(6);
        assertThat(baseline.perCycle()).extracting(CycleContribution::cycle).containsExactly(C6, C5, C4, C3, C2, C1);
        assertThat(baseline.perCycle()).extracting(CycleContribution::cycleLength).containsExactly(33, 32, 31, 30, 29, 28);
        assertThat(baseline.perCycle()).extracting(CycleContribution::remainingDays).containsExactly(27, 26, 25, 24, 23, 22);
        assertThat(baseline.perCycle()).extracting(c -> c.remaining().toPlainString())
                .containsExactly("2700.00", "1690.00", "1250.00", "1500.00", "1381.00", "1100.00");
        assertThat(baseline.perCycle()).extracting(c -> c.remainingNormalized().toPlainString())
                .containsExactly("2400.00", "1560.00", "1200.00", "1500.00", "1441.04", "1200.00");
        assertThat(baseline.median()).isEqualByComparingTo("1470.52");
        assertThat(baseline.p25()).isEqualByComparingTo("1260.26");
        assertThat(baseline.p75()).isEqualByComparingTo("1545.00");
        assertThat(baseline.typicalPerDay()).isEqualByComparingTo("61.27");
    }

    @Test
    void cyclesOfDifferentLengthAreComparedOnTheNormalisedHorizonNotOnRawTotals() {
        when(payCycleService.history(USER, 6)).thenReturn(List.of(C6, C1));
        stubSeries(C1, constantFrom(C1, 7, "100.00"));   // 22 remaining days → raw 2200
        stubSeries(C6, constantFrom(C6, 7, "100.00"));   // 27 remaining days → raw 2700

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, D, R);

        assertThat(baseline.perCycle()).extracting(c -> c.remaining().toPlainString()).containsExactly("2700.00", "2200.00");
        assertThat(baseline.perCycle()).extracting(c -> c.remainingNormalized().toPlainString()).containsExactly("2400.00", "2400.00");
        assertThat(baseline.median()).isEqualByComparingTo("2400.00");
        assertThat(baseline.typicalPerDay()).isEqualByComparingTo("100.00");
    }

    @Test
    void oneUsableCycleUsesTheSyntheticBand() {
        when(payCycleService.history(USER, 6)).thenReturn(List.of(C1));
        stubSeries(C1, Map.of(7, "600.00", 28, "500.00"));

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, D, R);

        assertThat(baseline.cyclesUsed()).isEqualTo(1);
        assertThat(baseline.median()).isEqualByComparingTo("1200.00");
        assertThat(baseline.p25()).isEqualByComparingTo("900.00");
        assertThat(baseline.p75()).isEqualByComparingTo("1500.00");
        assertThat(baseline.typicalPerDay()).isEqualByComparingTo("50.00");
    }

    @Test
    void twoUsableCyclesUseTheMedianAndTheSyntheticBand() {
        when(payCycleService.history(USER, 6)).thenReturn(List.of(C2, C1));
        stubSeries(C1, Map.of(7, "600.00", 28, "500.00"));
        stubSeries(C2, Map.of(29, "1381.00"));

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, D, R);

        assertThat(baseline.cyclesUsed()).isEqualTo(2);
        assertThat(baseline.median()).isEqualByComparingTo("1320.52");   // (1200 + 1441.04) / 2
        assertThat(baseline.p25()).isEqualByComparingTo("990.39");        // 1320.52 × 0.75 = 990.39
        assertThat(baseline.p75()).isEqualByComparingTo("1650.65");       // 1320.52 × 1.25
        assertThat(baseline.typicalPerDay()).isEqualByComparingTo("55.02"); // 1320.52 / 24 = 55.0216…
    }

    @Test
    void threeUsableCyclesSwitchFromTheBandToRealQuantiles() {
        when(payCycleService.history(USER, 6)).thenReturn(List.of(C6, C3, C1));
        stubSeries(C1, Map.of(7, "600.00", 28, "500.00"));   // 1200.00
        stubSeries(C3, constantFrom(C3, 7, "62.50"));         // 1500.00
        stubSeries(C6, constantFrom(C6, 7, "100.00"));        // 2400.00

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, D, R);

        assertThat(baseline.cyclesUsed()).isEqualTo(3);
        assertThat(baseline.median()).isEqualByComparingTo("1500.00");
        assertThat(baseline.p25()).isEqualByComparingTo("1350.00");   // rank 0.5 → 1200 + 0.5 × 300, not 1500 × 0.75 = 1125
        assertThat(baseline.p75()).isEqualByComparingTo("1950.00");   // rank 1.5 → 1500 + 0.5 × 900, not 1500 × 1.25 = 1875
        assertThat(baseline.typicalPerDay()).isEqualByComparingTo("62.50");
    }

    @Test
    void zeroHistoryUserGetsAnEmptyBaselineWithNulls() {
        when(payCycleService.history(USER, 6)).thenReturn(List.of());

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, D, R);

        assertThat(baseline.cyclesUsed()).isZero();
        assertThat(baseline.perCycle()).isEmpty();
        assertThat(baseline.median()).isNull();
        assertThat(baseline.p25()).isNull();
        assertThat(baseline.p75()).isNull();
        assertThat(baseline.typicalPerDay()).isNull();
        verifyNoInteractions(queryService);
    }

    @Test
    void cyclesShorterThanTheCurrentDayIndexAreListedButNotCounted() {
        when(payCycleService.history(USER, 6)).thenReturn(List.of(C4, C3, C2, C1));
        stubSeries(C1, Map.of(28, "500.00"));
        stubSeries(C2, Map.of(29, "500.00"));
        stubSeries(C3, Map.of(30, "500.00"));
        stubSeries(C4, Map.of(30, "999.00", 31, "40.00"));

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, 30, 3);

        assertThat(baseline.cyclesUsed()).isEqualTo(1);
        assertThat(baseline.perCycle()).hasSize(4);
        assertThat(baseline.perCycle()).extracting(CycleContribution::remainingDays).containsExactly(1, 0, 0, 0);
        assertThat(baseline.perCycle()).extracting(CycleContribution::usable).containsExactly(true, false, false, false);
        assertThat(baseline.perCycle().get(1).remainingNormalized()).isNull();
        assertThat(baseline.perCycle().get(1).remaining()).isEqualByComparingTo("0.00");   // d ≥ L → nothing after day d
        assertThat(baseline.perCycle().getFirst().remaining()).isEqualByComparingTo("40.00");
        assertThat(baseline.perCycle().getFirst().remainingNormalized()).isEqualByComparingTo("120.00"); // 40 × 3 / 1
        assertThat(baseline.median()).isEqualByComparingTo("120.00");
        assertThat(baseline.typicalPerDay()).isEqualByComparingTo("40.00");
    }

    @Test
    void allCyclesShorterThanDayIndexYieldZeroUsableCycles() {
        when(payCycleService.history(USER, 6)).thenReturn(List.of(C2, C1));
        stubSeries(C1, Map.of(28, "500.00"));
        stubSeries(C2, Map.of(29, "500.00"));

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, 40, 2);

        assertThat(baseline.cyclesUsed()).isZero();
        assertThat(baseline.perCycle()).hasSize(2);
        assertThat(baseline.median()).isNull();
        assertThat(baseline.typicalPerDay()).isNull();
    }

    @Test
    void zeroDaysRemainingNormalisesToZeroWithoutDividingByZero() {
        when(payCycleService.history(USER, 6)).thenReturn(List.of(C3, C2, C1));
        stubSeries(C1, Map.of(7, "600.00", 28, "500.00"));
        stubSeries(C2, Map.of(29, "1381.00"));
        stubSeries(C3, constantFrom(C3, 7, "62.50"));

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, D, 0);

        assertThat(baseline.cyclesUsed()).isEqualTo(3);
        assertThat(baseline.perCycle()).extracting(c -> c.remainingNormalized().toPlainString())
                .containsExactly("0.00", "0.00", "0.00");
        assertThat(baseline.median()).isEqualByComparingTo("0.00");
        assertThat(baseline.p25()).isEqualByComparingTo("0.00");
        assertThat(baseline.p75()).isEqualByComparingTo("0.00");
        assertThat(baseline.typicalPerDay()).isNull();
    }

    @Test
    void historyIsCappedAtSixEvenWhenMoreCyclesExist() {
        PayCycle c0 = closed(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 9));
        when(payCycleService.history(USER, 6)).thenReturn(List.of(C6, C5, C4, C3, C2, C1, c0));
        for (PayCycle cycle : List.of(C1, C2, C3, C4, C5, C6)) {
            stubSeries(cycle, constantFrom(cycle, 7, "10.00"));
        }

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, D, R, 10);

        assertThat(baseline.cyclesUsed()).isEqualTo(6);
        assertThat(baseline.perCycle()).extracting(CycleContribution::cycle).doesNotContain(c0);
        verify(payCycleService, never()).history(eq(USER), eq(10));
        verify(queryService, never()).dailyVariableTotals(WALLET, USER, c0.start(), c0.end());
    }

    @Test
    void smallerMaxCyclesIsPassedThroughAndZeroSkipsTheLookup() {
        when(payCycleService.history(USER, 2)).thenReturn(List.of(C6, C5));
        stubSeries(C5, constantFrom(C5, 7, "10.00"));
        stubSeries(C6, constantFrom(C6, 7, "10.00"));

        assertThat(service.baseline(USER, WALLET, CURRENT, D, R, 2).cyclesUsed()).isEqualTo(2);
        assertThat(service.baseline(USER, WALLET, CURRENT, D, R, 0).cyclesUsed()).isZero();
        verify(payCycleService, never()).history(any(), eq(0));
    }

    @Test
    void cyclesNotStrictlyBeforeTheCurrentCycleAreIgnored() {
        PayCycle overlapping = closed(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 8));
        when(payCycleService.history(USER, 6)).thenReturn(List.of(overlapping, C6));
        stubSeries(C6, constantFrom(C6, 7, "100.00"));

        HistoricalBaseline baseline = service.baseline(USER, WALLET, CURRENT, D, R);

        assertThat(baseline.cyclesUsed()).isEqualTo(1);
        assertThat(baseline.perCycle()).extracting(CycleContribution::cycle).containsExactly(C6);
        verify(queryService, never()).dailyVariableTotals(eq(WALLET), eq(USER), eq(overlapping.start()), any());
    }

    @Test
    void rejectsNegativeDayIndexOrHorizon() {
        assertThatThrownBy(() -> service.baseline(USER, WALLET, CURRENT, -1, R)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.baseline(USER, WALLET, CURRENT, D, -1)).isInstanceOf(IllegalArgumentException.class);
        verify(payCycleService, never()).history(any(), anyInt());
    }

    @Test
    void queryServiceIsInvokedExactlyOnceForEachHistoricalCycleWithTheFullCycleRangeAndNeverForTheCurrentCycle() {
        when(payCycleService.history(USER, 6)).thenReturn(List.of(C6, C5));
        stubSeries(C5, constantFrom(C5, 7, "10.00"));
        stubSeries(C6, constantFrom(C6, 7, "10.00"));

        service.baseline(USER, WALLET, CURRENT, D, R);

        verify(queryService, times(1)).dailyVariableTotals(WALLET, USER, C6.start(), C6.end());
        verify(queryService, times(1)).dailyVariableTotals(WALLET, USER, C5.start(), C5.end());
        verify(queryService, never()).dailyVariableTotals(WALLET, USER, CURRENT.start(), CURRENT.end());
        verify(queryService, never()).dailyVariableTotals(eq(WALLET), eq(USER), any(), eq(CURRENT.end()));
    }

    @Test
    void dayIndexBoundaryLastDayCountsButExactCycleLengthDoesNot() {
        // 10-day cycle: day 9 (== d) must be excluded by skip(d), day 10 (== d+1, the last day) must be included.
        PayCycle cycle = closed(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));
        when(payCycleService.history(USER, 6)).thenReturn(List.of(cycle));
        stubSeries(cycle, Map.of(9, "999.00", 10, "77.00"));

        HistoricalBaseline atLastDay = service.baseline(USER, WALLET, CURRENT, 9, 1);
        assertThat(atLastDay.perCycle()).extracting(CycleContribution::remainingDays).containsExactly(1);
        assertThat(atLastDay.perCycle()).extracting(CycleContribution::usable).containsExactly(true);
        assertThat(atLastDay.perCycle().getFirst().remaining()).isEqualByComparingTo("77.00");
        assertThat(atLastDay.perCycle().getFirst().remainingNormalized()).isEqualByComparingTo("77.00");
        assertThat(atLastDay.cyclesUsed()).isEqualTo(1);

        HistoricalBaseline atCycleLength = service.baseline(USER, WALLET, CURRENT, 10, 1);
        assertThat(atCycleLength.perCycle()).extracting(CycleContribution::remainingDays).containsExactly(0);
        assertThat(atCycleLength.perCycle()).extracting(CycleContribution::usable).containsExactly(false);
        assertThat(atCycleLength.perCycle().getFirst().remaining()).isEqualByComparingTo("0.00");
        assertThat(atCycleLength.perCycle().getFirst().remainingNormalized()).isNull();
        assertThat(atCycleLength.cyclesUsed()).isZero();
    }

    private static PayCycle closed(LocalDate start, LocalDate end) {
        return PayCycle.closed(USER, WALLET, start, end);
    }

    /** Zero-filled full-cycle series with the given amounts at the given 1-based day indexes. */
    private void stubSeries(PayCycle cycle, Map<Integer, String> byDayIndex) {
        List<DailyTotal> series = new ArrayList<>();
        for (int k = 1; k <= cycle.lengthDays(); k++) {
            String amount = byDayIndex.getOrDefault(k, "0.00");
            series.add(new DailyTotal(cycle.start().plusDays(k - 1L), new BigDecimal(amount)));
        }
        when(queryService.dailyVariableTotals(WALLET, USER, cycle.start(), cycle.end())).thenReturn(series);
    }

    private static Map<Integer, String> constantFrom(PayCycle cycle, int fromDayIndex, String amount) {
        Map<Integer, String> map = new java.util.HashMap<>();
        for (int k = fromDayIndex; k <= cycle.lengthDays(); k++) {
            map.put(k, amount);
        }
        return map;
    }
}
