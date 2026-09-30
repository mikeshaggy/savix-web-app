package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.ForecastTrajectoryDto.BandPoint;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleContribution;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastTrajectoryBuilderTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 9, 9);

    private static List<DailyTotal> daily(String... amounts) {
        List<DailyTotal> out = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            out.add(new DailyTotal(START.plusDays(i), new BigDecimal(amounts[i])));
        }
        return out;
    }

    /** A closed cycle of {@code length} days spending {@code perDay} every day. */
    private static CycleContribution cycle(int length, String perDay) {
        LocalDate start = START.minusDays(40);
        List<BigDecimal> amounts = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            amounts.add(new BigDecimal(perDay));
        }
        return new CycleContribution(PayCycle.closed(USER, 1, start, start.plusDays(length - 1)), length,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, null, amounts);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void currentSeriesIsCumulativeFromDayOneThroughToday() {
        ForecastTrajectoryDto t = ForecastTrajectoryBuilder.build(START, 30, daily("94.98", "0.00", "335.64"), List.of());

        assertThat(t.current()).extracting(ForecastTrajectoryDto.Point::dayIndex).containsExactly(1, 2, 3);
        assertThat(t.current()).extracting(ForecastTrajectoryDto.Point::date)
                .containsExactly(START, START.plusDays(1), START.plusDays(2));
        assertThat(t.current()).extracting(ForecastTrajectoryDto.Point::cumulative)
                .containsExactly(bd("94.98"), bd("94.98"), bd("430.62"));
        assertThat(t.typical()).isEmpty();
    }

    @Test
    void threeOrMoreCyclesGiveR7QuantilesPerDay() {
        // cumulative at day 2: 20, 40, 60, 80 → p25 35, median 50, p75 65 (R-7)
        ForecastTrajectoryDto t = ForecastTrajectoryBuilder.build(START, 3, daily("1.00"),
                List.of(cycle(3, "10.00"), cycle(3, "20.00"), cycle(3, "30.00"), cycle(3, "40.00")));

        BandPoint day2 = t.typical().get(1);
        assertThat(day2.dayIndex()).isEqualTo(2);
        assertThat(day2.date()).isEqualTo(START.plusDays(1));
        assertThat(day2.p25()).isEqualByComparingTo("35.00");
        assertThat(day2.median()).isEqualByComparingTo("50.00");
        assertThat(day2.p75()).isEqualByComparingTo("65.00");
        assertThat(day2.cycles()).isEqualTo(4);
    }

    @Test
    void oneOrTwoCyclesUseTheStage43PlusMinus25PercentBand() {
        ForecastTrajectoryDto t = ForecastTrajectoryBuilder.build(START, 2, daily("1.00"),
                List.of(cycle(2, "100.00"), cycle(2, "300.00")));

        BandPoint day1 = t.typical().get(0);
        assertThat(day1.median()).isEqualByComparingTo("200.00");
        assertThat(day1.p25()).isEqualByComparingTo("150.00");
        assertThat(day1.p75()).isEqualByComparingTo("250.00");
        assertThat(day1.cycles()).isEqualTo(2);
    }

    @Test
    void shorterCyclesDropOutAndUnreachedDaysAreOmittedNotPadded() {
        // current cycle has 33 days; history cycles last 30 and 31 days
        ForecastTrajectoryDto t = ForecastTrajectoryBuilder.build(START, 33, daily("1.00"),
                List.of(cycle(30, "10.00"), cycle(31, "10.00")));

        assertThat(t.typical()).hasSize(31);
        assertThat(t.typical().get(29).cycles()).isEqualTo(2); // day 30
        assertThat(t.typical().get(30).cycles()).isEqualTo(1); // day 31 — only the 31-day cycle
        assertThat(t.typical().get(30).median()).isEqualByComparingTo("310.00");
        assertThat(t.typical()).extracting(BandPoint::dayIndex).doesNotContain(32, 33);
    }

    @Test
    void contributionsWithoutADailySeriesAreIgnoredAndZeroHistoryGivesNoBand() {
        CycleContribution legacyShape = new CycleContribution(
                PayCycle.closed(USER, 1, START.minusDays(40), START.minusDays(11)), 30,
                bd("3000.00"), bd("2400.00"), 24, bd("2400.00"));

        ForecastTrajectoryDto t = ForecastTrajectoryBuilder.build(START, 30, daily(), List.of(legacyShape));

        assertThat(t.current()).isEmpty();
        assertThat(t.typical()).isEmpty();
    }
}
