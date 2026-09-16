package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.calculation.Quantiles;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;

/**
 * Forecast v2 arithmetic (Stage 4). Pure: the caller resolves the cycle, runs the queries and decides
 * whether a forecast exists at all. Stage 4.4 — the robust current-cycle pace — lives here; the blend,
 * range and status of Stage 4.5 are added to this calculator later and must not be assumed yet.
 */
@Component
public class ForecastV2Calculator {

    /**
     * Current-cycle pace over the pace-eligible daily variable totals from the cycle start through today,
     * as returned by {@code AnalyticsTransactionQueryService.dailyVariableTotals(start, today)}: one entry
     * per day, zero-spend days included. Zero days are real observations and pull the median down.
     *
     * @param daily         zero-filled daily series, {@code start..today} — never empty
     * @param daysRemaining days left after today in the open cycle ({@code R}), {@code ≥ 0}
     */
    public CurrentPace currentPace(List<DailyTotal> daily, int daysRemaining) {
        if (daily == null || daily.isEmpty()) {
            throw new IllegalArgumentException("current pace needs at least one observed day");
        }
        if (daysRemaining < 0) {
            throw new IllegalArgumentException("daysRemaining must not be negative: " + daysRemaining);
        }
        List<BigDecimal> amounts = daily.stream().map(DailyTotal::amount).map(v -> money(v)).toList();
        int daysElapsed = amounts.size();
        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal trimmedDailyPace = Quantiles.median(amounts);
        BigDecimal rawDailyBurnRate = sum.divide(BigDecimal.valueOf(daysElapsed), SCALE, ROUNDING);
        BigDecimal paceProjection = money(trimmedDailyPace.multiply(BigDecimal.valueOf(daysRemaining)));

        return new CurrentPace(daysElapsed, money(sum), trimmedDailyPace, rawDailyBurnRate, paceProjection);
    }

    /**
     * @param daysElapsed       observed days ({@code start..today} inclusive)
     * @param variableToDate    Σ daily (pace-eligible variable spend so far)
     * @param trimmedDailyPace  median of the daily totals — the robust pace
     * @param rawDailyBurnRate  Σ daily / daysElapsed — the legacy-style mean, kept for comparison
     * @param paceProjection    {@code trimmedDailyPace × daysRemaining}
     */
    public record CurrentPace(
            int daysElapsed,
            BigDecimal variableToDate,
            BigDecimal trimmedDailyPace,
            BigDecimal rawDailyBurnRate,
            BigDecimal paceProjection) {
    }
}
