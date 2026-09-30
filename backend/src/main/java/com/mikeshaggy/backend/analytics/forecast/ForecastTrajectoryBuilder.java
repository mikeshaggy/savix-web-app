package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.ForecastTrajectoryDto.BandPoint;
import com.mikeshaggy.backend.analytics.forecast.ForecastTrajectoryDto.Point;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleContribution;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.calculation.Quantiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;

/**
 * Stage 5.4 — cumulative series for the Forecast trajectory chart. Pure: it only re-shapes the pace-eligible daily
 * series {@link ForecastService} and {@link HistoricalVariableSpendService} already loaded, so the chart can never
 * disagree with the pace, the baseline or the one-off impacts.
 */
final class ForecastTrajectoryBuilder {

    private static final BigDecimal P25 = new BigDecimal("0.25");
    private static final BigDecimal P75 = new BigDecimal("0.75");
    private static final BigDecimal BAND_LOW = new BigDecimal("0.75");
    private static final BigDecimal BAND_HIGH = new BigDecimal("1.25");

    private ForecastTrajectoryBuilder() {
    }

    /**
     * @param start            day 1 of the current cycle
     * @param cycleLengthDays  days in the current cycle ({@code D}); the band runs day 1 … D
     * @param currentDaily     the current cycle's zero-filled pace-eligible daily totals, day 1 … today
     * @param baselineCycles   the historical baseline's per-cycle contributions (their {@code dailyAmounts})
     */
    static ForecastTrajectoryDto build(LocalDate start, int cycleLengthDays, List<DailyTotal> currentDaily,
                                       List<CycleContribution> baselineCycles) {
        return new ForecastTrajectoryDto(current(start, currentDaily), typical(start, cycleLengthDays, baselineCycles));
    }

    private static List<Point> current(LocalDate start, List<DailyTotal> daily) {
        List<Point> points = new ArrayList<>(daily.size());
        BigDecimal running = BigDecimal.ZERO;
        for (int i = 0; i < daily.size(); i++) {
            running = running.add(daily.get(i).amount());
            points.add(new Point(i + 1, start.plusDays(i), money(running)));
        }
        return points;
    }

    private static List<BandPoint> typical(LocalDate start, int cycleLengthDays, List<CycleContribution> cycles) {
        List<List<BigDecimal>> cumulativeByCycle = cycles.stream()
                .map(CycleContribution::dailyAmounts)
                .filter(daily -> !daily.isEmpty())
                .map(ForecastTrajectoryBuilder::cumulative)
                .toList();
        List<BandPoint> band = new ArrayList<>(Math.max(cycleLengthDays, 0));
        for (int day = 1; day <= cycleLengthDays; day++) {
            List<BigDecimal> values = new ArrayList<>();
            for (List<BigDecimal> cumulative : cumulativeByCycle) {
                if (cumulative.size() >= day) {
                    values.add(cumulative.get(day - 1));
                }
            }
            if (values.isEmpty()) {
                continue;
            }
            BigDecimal median = Quantiles.median(values);
            BigDecimal p25;
            BigDecimal p75;
            if (values.size() < 3) {
                p25 = money(median.multiply(BAND_LOW));
                p75 = money(median.multiply(BAND_HIGH));
            } else {
                p25 = Quantiles.percentile(values, P25);
                p75 = Quantiles.percentile(values, P75);
            }
            band.add(new BandPoint(day, start.plusDays(day - 1), p25, median, p75, values.size()));
        }
        return band;
    }

    private static List<BigDecimal> cumulative(List<BigDecimal> daily) {
        List<BigDecimal> out = new ArrayList<>(daily.size());
        BigDecimal running = BigDecimal.ZERO;
        for (BigDecimal amount : daily) {
            running = running.add(amount == null ? BigDecimal.ZERO : amount);
            out.add(money(running));
        }
        return out;
    }
}
