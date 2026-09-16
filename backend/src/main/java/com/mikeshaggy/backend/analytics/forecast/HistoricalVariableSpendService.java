package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.calculation.Quantiles;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;

/**
 * Stage 4.3 — what the user typically still spends on variable expenses over the rest of a pay cycle,
 * learned from closed cycles.
 * <p>
 * For each of the last {@value #DEFAULT_CYCLES} closed cycles {@code c} (length {@code L_c}) the
 * pace-eligible, zero-filled daily variable totals {@code v_c[1..L_c]} are read for the whole cycle and
 * the spend after the current day index {@code d} is taken: {@code remaining_c = Σ v_c[d+1..L_c]} over
 * {@code remainingDays_c = max(L_c − d, 0)} days. Cycles have different lengths, so raw remaining totals are
 * not comparable with each other nor with the current cycle; every contribution is normalised to the
 * current remaining horizon {@code R}: {@code remainingNorm_c = remaining_c × R / remainingDays_c}. A cycle
 * with {@code remainingDays_c == 0} (not longer than {@code d}: {@code L_c ≤ d}) is listed but contributes nothing.
 * <p>
 * Not memoised: the caller computes it once per request and passes it down.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoricalVariableSpendService {

    public static final int DEFAULT_CYCLES = 6;

    private static final BigDecimal P25 = new BigDecimal("0.25");
    private static final BigDecimal P75 = new BigDecimal("0.75");
    /** Synthetic ±25 % band around the median when only one or two cycles are usable. */
    private static final BigDecimal BAND_LOW = new BigDecimal("0.75");
    private static final BigDecimal BAND_HIGH = new BigDecimal("1.25");

    private final PayCycleService payCycleService;
    private final AnalyticsTransactionQueryService analyticsTransactionQueryService;

    public HistoricalBaseline baseline(UUID userId, Integer walletId, PayCycle currentCycle,
                                       int dayIndex, int daysRemaining) {
        return baseline(userId, walletId, currentCycle, dayIndex, daysRemaining, DEFAULT_CYCLES);
    }

    /**
     * @param currentCycle  the open cycle the forecast is for; only cycles ending before its start are used
     * @param dayIndex      current 1-based day index {@code d} within {@code currentCycle}
     * @param daysRemaining days left after today in {@code currentCycle} ({@code R})
     * @param maxCycles     history depth {@code n}, capped at {@value #DEFAULT_CYCLES}
     */
    public HistoricalBaseline baseline(UUID userId, Integer walletId, PayCycle currentCycle,
                                       int dayIndex, int daysRemaining, int maxCycles) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(currentCycle, "currentCycle");
        if (dayIndex < 0) {
            throw new IllegalArgumentException("dayIndex must not be negative: " + dayIndex);
        }
        if (daysRemaining < 0) {
            throw new IllegalArgumentException("daysRemaining must not be negative: " + daysRemaining);
        }
        int n = Math.min(Math.max(maxCycles, 0), DEFAULT_CYCLES);
        if (n == 0) {
            return HistoricalBaseline.empty();
        }

        List<PayCycle> history = payCycleService.history(userId, n).stream()
                .filter(cycle -> cycle.state() == CycleState.CLOSED)
                .filter(cycle -> cycle.end().isBefore(currentCycle.start()))
                .limit(n)
                .toList();

        List<CycleContribution> contributions = new ArrayList<>(history.size());
        for (PayCycle cycle : history) {
            contributions.add(contribution(userId, walletId, cycle, dayIndex, daysRemaining));
        }
        return HistoricalBaseline.of(List.copyOf(contributions), daysRemaining);
    }

    private CycleContribution contribution(UUID userId, Integer walletId, PayCycle cycle,
                                           int dayIndex, int daysRemaining) {
        List<DailyTotal> daily = analyticsTransactionQueryService.dailyVariableTotals(
                walletId, userId, cycle.start(), cycle.end());
        int length = cycle.lengthDays();
        int remainingDays = Math.max(length - dayIndex, 0);

        // v_c[k] for k = d+1 .. L_c; the series is 1-based by day index, the list 0-based
        BigDecimal remaining = money(daily.stream()
                .skip(Math.min(dayIndex, daily.size()))
                .map(DailyTotal::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal remainingNormalized = remainingDays == 0
                ? null
                : remaining.multiply(BigDecimal.valueOf(daysRemaining))
                        .divide(BigDecimal.valueOf(remainingDays), SCALE, ROUNDING);

        return new CycleContribution(cycle, length, remaining, remainingDays, remainingNormalized);
    }

    /**
     * One closed cycle's share of the baseline. {@code remainingNormalized} is {@code null} — and the cycle
     * is not counted in {@link HistoricalBaseline#cyclesUsed} — when the cycle was not longer than the current
     * day index ({@code L_c ≤ d}: no day after {@code d} exists).
     */
    public record CycleContribution(
            PayCycle cycle,
            int cycleLength,
            BigDecimal remaining,
            int remainingDays,
            BigDecimal remainingNormalized) {

        public boolean usable() {
            return remainingNormalized != null;
        }
    }

    /**
     * Normalised remaining variable spend across the usable history. {@code median}, {@code p25},
     * {@code p75} and {@code typicalPerDay} are {@code null} without a usable cycle; {@code typicalPerDay}
     * is also {@code null} when {@code R == 0} (there is no remaining horizon to spread the median over —
     * every normalised contribution is zero then).
     */
    public record HistoricalBaseline(
            int cyclesUsed,
            BigDecimal median,
            BigDecimal p25,
            BigDecimal p75,
            List<CycleContribution> perCycle,
            BigDecimal typicalPerDay) {

        static HistoricalBaseline empty() {
            return new HistoricalBaseline(0, null, null, null, List.of(), null);
        }

        static HistoricalBaseline of(List<CycleContribution> perCycle, int daysRemaining) {
            List<BigDecimal> normalized = perCycle.stream()
                    .filter(CycleContribution::usable)
                    .map(CycleContribution::remainingNormalized)
                    .toList();
            int cyclesUsed = normalized.size();
            if (cyclesUsed == 0) {
                return new HistoricalBaseline(0, null, null, null, perCycle, null);
            }

            BigDecimal median = Quantiles.median(normalized);
            BigDecimal p25;
            BigDecimal p75;
            if (cyclesUsed < 3) {
                p25 = money(median.multiply(BAND_LOW));
                p75 = money(median.multiply(BAND_HIGH));
            } else {
                p25 = Quantiles.percentile(normalized, P25);
                p75 = Quantiles.percentile(normalized, P75);
            }
            BigDecimal typicalPerDay = daysRemaining == 0
                    ? null
                    : median.divide(BigDecimal.valueOf(daysRemaining), SCALE, ROUNDING);

            return new HistoricalBaseline(cyclesUsed, median, p25, p75, perCycle, typicalPerDay);
        }
    }
}
