package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService.CategoryAtDay;
import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService.CategoryDayBaseline;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyCategoryTotal;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 7.3 median-at-day-d category baseline: the median (never the average) of each closed cycle's cumulative
 * day-1..d spend, Stage 4 cycle and cutoff semantics, zeros counted, shorter cycles dropped, one query.
 */
class HistoricalCategorySpendServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final int WALLET = 1;
    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final PayCycle CURRENT = PayCycle.open(USER, WALLET, START, LocalDate.of(2026, 10, 9));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14); // day 6

    /** The audited anchors: six closed cycles before Sep 9, newest first (May 8 – Jun 9 is 33 days long). */
    private static final List<PayCycle> HISTORY = List.of(
            closed("2026-08-10", "2026-09-08"),
            closed("2026-07-10", "2026-08-09"),
            closed("2026-06-10", "2026-07-09"),
            closed("2026-05-08", "2026-06-09"),
            closed("2026-04-10", "2026-05-07"),
            closed("2026-03-10", "2026-04-09"));

    private PayCycleService payCycleService;
    private AnalyticsTransactionQueryService queries;
    private HistoricalCategorySpendService service;

    @BeforeEach
    void setUp() {
        payCycleService = mock(PayCycleService.class);
        queries = mock(AnalyticsTransactionQueryService.class);
        service = new HistoricalCategorySpendService(payCycleService, queries);
        when(payCycleService.history(USER, 6)).thenReturn(HISTORY);
    }

    @Test
    void groceriesMedianAtDay6_is162_34_notTheAverage() {
        List<DailyCategoryTotal> rows = new ArrayList<>();
        // day-1..6 groceries per cycle: 150.00, 160.00, 162.00, 162.68, 170.00, 200.00 → median 162.34, mean 167.45
        String[] perCycle = {"150.00", "160.00", "162.00", "162.68", "170.00", "200.00"};
        for (int i = 0; i < HISTORY.size(); i++) {
            rows.add(groceries(HISTORY.get(i).start().plusDays(2), perCycle[i]));
            rows.add(groceries(HISTORY.get(i).start().plusDays(6), "999.00")); // day 7: after the cutoff
        }
        rows.add(groceries(START, "94.98"));
        rows.add(groceries(START.plusDays(1), "120.00"));
        rows.add(groceries(START.plusDays(2), "99.86"));
        when(queries.dailyCategoryVariableTotals(WALLET, USER, LocalDate.of(2026, 3, 10), TODAY)).thenReturn(rows);

        CategoryDayBaseline baseline = service.atDay(USER, WALLET, CURRENT, TODAY);

        assertThat(baseline.dayIndex()).isEqualTo(6);
        assertThat(baseline.cyclesUsed()).isEqualTo(6);
        assertThat(baseline.categories()).singleElement().satisfies(groceries -> {
            assertThat(groceries.currentAmount()).isEqualByComparingTo("314.84");
            assertThat(groceries.medianAtDay()).isEqualByComparingTo("162.34");
            assertThat(groceries.perCycle()).extracting(BigDecimal::toPlainString)
                    .containsExactly("150.00", "160.00", "162.00", "162.68", "170.00", "200.00");
        });
        verify(queries, times(1)).dailyCategoryVariableTotals(any(), any(), any(), any());
    }

    @Test
    void aCycleWithoutSpendInTheCategory_countsAsZero() {
        List<DailyCategoryTotal> rows = new ArrayList<>();
        // spend in 3 of 6 cycles only: values 0, 0, 0, 100, 200, 300 → median 50.00
        rows.add(groceries(HISTORY.get(0).start(), "100.00"));
        rows.add(groceries(HISTORY.get(1).start(), "200.00"));
        rows.add(groceries(HISTORY.get(2).start(), "300.00"));
        when(queries.dailyCategoryVariableTotals(eq(WALLET), eq(USER), any(), eq(TODAY))).thenReturn(rows);

        CategoryAtDay groceries = service.atDay(USER, WALLET, CURRENT, TODAY).categories().getFirst();

        assertThat(groceries.medianAtDay()).isEqualByComparingTo("50.00");
        assertThat(groceries.currentAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void cyclesShorterThanDayD_areDropped_neverPadded() {
        // day 31: only the 33-day May 8 – Jun 9 cycle and the 31-day cycles reach it
        LocalDate day31 = START.plusDays(30);
        PayCycle longCurrent = PayCycle.open(USER, WALLET, START, LocalDate.of(2026, 10, 12));
        when(queries.dailyCategoryVariableTotals(eq(WALLET), eq(USER), any(), eq(day31))).thenReturn(List.of());

        CategoryDayBaseline baseline = service.atDay(USER, WALLET, longCurrent, day31);

        assertThat(baseline.cycles()).allMatch(cycle -> cycle.lengthDays() >= 31);
        assertThat(baseline.cycles()).extracting(PayCycle::start).doesNotContain(LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 4, 10)); // 30-day and 28-day cycles
    }

    @Test
    void onlyClosedCyclesEndingBeforeTheCurrentStart_atMostSix() {
        List<PayCycle> withExtra = new ArrayList<>(HISTORY);
        withExtra.add(closed("2026-02-10", "2026-03-09"));
        when(payCycleService.history(USER, 6)).thenReturn(withExtra);
        when(queries.dailyCategoryVariableTotals(eq(WALLET), eq(USER), any(), eq(TODAY))).thenReturn(List.of());

        assertThat(service.atDay(USER, WALLET, CURRENT, TODAY).cyclesUsed()).isEqualTo(6);
    }

    @Test
    void noHistory_noMedian_currentStillReported() {
        when(payCycleService.history(USER, 6)).thenReturn(List.of());
        when(queries.dailyCategoryVariableTotals(WALLET, USER, START, TODAY))
                .thenReturn(List.of(groceries(START, "94.98")));

        CategoryDayBaseline baseline = service.atDay(USER, WALLET, CURRENT, TODAY);

        assertThat(baseline.cyclesUsed()).isZero();
        assertThat(baseline.categories()).singleElement().satisfies(groceries -> {
            assertThat(groceries.medianAtDay()).isNull();
            assertThat(groceries.currentAmount()).isEqualByComparingTo("94.98");
        });
    }

    @Test
    void todayBeforeTheCycleStart_isRejected() {
        assertThatThrownBy(() -> service.atDay(USER, WALLET, CURRENT, START.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DailyCategoryTotal groceries(LocalDate day, String amount) {
        return new DailyCategoryTotal(day, 3, "Groceries", null, new BigDecimal(amount));
    }

    private static PayCycle closed(String start, String end) {
        return PayCycle.closed(USER, WALLET, LocalDate.parse(start), LocalDate.parse(end));
    }
}
