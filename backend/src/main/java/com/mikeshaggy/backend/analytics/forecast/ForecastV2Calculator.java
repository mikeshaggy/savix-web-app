package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.HistoricalBaseline;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.calculation.Quantiles;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;

/**
 * Forecast v2 arithmetic (Stage 4). Pure: the caller resolves the cycle, runs the queries, assembles the
 * committed occurrences and decides whether a forecast exists at all (PAY_CYCLE, salary wallet, OPEN —
 * Stage 4.7). Stage 4.4 is {@link #currentPace}; Stage 4.5 is {@link #historyWeight} and {@link #forecast}.
 */
@Component
public class ForecastV2Calculator {

    /** Scale of the dimensionless history weight; money results keep {@link com.mikeshaggy.backend.common.calculation.CalculationUtils#SCALE}. */
    static final int WEIGHT_SCALE = 4;
    static final BigDecimal WEIGHT_MIN = new BigDecimal("0.3");
    static final BigDecimal WEIGHT_MAX = new BigDecimal("0.9");
    /** Cap on the history weight while fewer than {@value #FULL_HISTORY_CYCLES} closed cycles back the baseline. */
    static final BigDecimal SPARSE_HISTORY_CAP = new BigDecimal("0.5");
    static final int FULL_HISTORY_CYCLES = 3;
    static final BigDecimal PESSIMISTIC_PACE_FACTOR = new BigDecimal("1.25");
    static final BigDecimal OPTIMISTIC_PACE_FACTOR = new BigDecimal("0.75");

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
     * Weight of the historical median against the current pace (Stage 4.5):
     * <pre>
     * cyclesUsed == 0 → 0
     * cyclesUsed &lt; 3  → min(0.5, clamp(1 − d/D, 0.3, 0.9))
     * cyclesUsed ≥ 3  → clamp(1 − d/D, 0.3, 0.9)
     * </pre>
     * {@code d/D} is a decimal ratio ({@value #WEIGHT_SCALE} dp, HALF_UP) — never an integer division.
     *
     * @param dayIndex        elapsed days {@code d} (1-based day index of today), {@code ≥ 0}; may exceed
     *                        {@code cycleLengthDays} while awaiting a late salary — the clamp absorbs it
     * @param cycleLengthDays {@code D = end − start + 1} of the open cycle, {@code ≥ 1}
     */
    public BigDecimal historyWeight(int cyclesUsed, int dayIndex, int cycleLengthDays) {
        if (cycleLengthDays < 1) {
            throw new IllegalArgumentException("cycleLengthDays must be positive: " + cycleLengthDays);
        }
        if (dayIndex < 0) {
            throw new IllegalArgumentException("dayIndex must not be negative: " + dayIndex);
        }
        if (cyclesUsed <= 0) {
            return BigDecimal.ZERO.setScale(WEIGHT_SCALE, ROUNDING);
        }
        BigDecimal elapsedRatio = BigDecimal.valueOf(dayIndex)
                .divide(BigDecimal.valueOf(cycleLengthDays), WEIGHT_SCALE, ROUNDING);
        BigDecimal weight = BigDecimal.ONE.subtract(elapsedRatio).max(WEIGHT_MIN).min(WEIGHT_MAX);
        if (cyclesUsed < FULL_HISTORY_CYCLES) {
            weight = weight.min(SPARSE_HISTORY_CAP);
        }
        return weight.setScale(WEIGHT_SCALE, ROUNDING);
    }

    /**
     * Blend, range and status (Stage 4.5). Terminology: the <em>pessimistic</em> variable-spend path
     * (history p75, pace × 1.25) yields the <em>low</em> end balance; the <em>optimistic</em> path (p25,
     * pace × 0.75) yields the <em>high</em> end balance — so {@code low ≤ typical ≤ high} holds whenever
     * {@code p25 ≤ median ≤ p75}, which {@link HistoricalBaseline} guarantees.
     * <p>
     * Committed money is derived from the occurrences themselves: only rows bucketed
     * {@link FixedOccurrenceBucket#OVERDUE}, {@link FixedOccurrenceBucket#DUE_SOON} or
     * {@link FixedOccurrenceBucket#LATER_THIS_CYCLE} count (= Stage 3 {@code overdueAmount + remainingAmount});
     * {@link FixedOccurrenceBucket#AFTER_PAYDAY} display rows and paid rows never do — neither for
     * {@code committed} nor for the status walk.
     */
    public ForecastV2 forecast(ForecastInput input) {
        Objects.requireNonNull(input, "input");
        CurrentPace pace = input.currentPace();
        HistoricalBaseline baseline = input.baseline();
        int daysRemaining = input.daysRemaining();
        if (pace.daysElapsed() != input.dayIndex()) {
            // both are d: the pace series runs start..today, so its length is the 1-based day index of today
            throw new IllegalArgumentException("dayIndex " + input.dayIndex()
                    + " disagrees with the observed series length " + pace.daysElapsed());
        }

        BigDecimal historyWeight = historyWeight(baseline.cyclesUsed(), input.dayIndex(), input.cycleLengthDays());
        BigDecimal paceProjection = pace.paceProjection();

        BigDecimal expectedVariableRemaining = blend(historyWeight, baseline.median(), paceProjection, BigDecimal.ONE);
        BigDecimal pessimisticVariableRemaining = blend(historyWeight, baseline.p75(), paceProjection, PESSIMISTIC_PACE_FACTOR);
        BigDecimal optimisticVariableRemaining = blend(historyWeight, baseline.p25(), paceProjection, OPTIMISTIC_PACE_FACTOR);

        List<CommittedOccurrence> committedOccurrences = input.occurrences().stream()
                .filter(CommittedOccurrence::committed)
                .sorted(Comparator.comparing(CommittedOccurrence::dueDate))
                .toList();
        BigDecimal committed = money(committedOccurrences.stream()
                .map(CommittedOccurrence::expectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal walletBalance = money(input.walletBalance());
        BigDecimal discretionaryNow = walletBalance.subtract(committed);
        BigDecimal discretionaryPerDay = discretionaryNow
                .divide(BigDecimal.valueOf(Math.max(daysRemaining, 1)), SCALE, ROUNDING);

        BigDecimal expectedEndBalanceTypical = discretionaryNow.subtract(expectedVariableRemaining);
        BigDecimal expectedEndBalanceLow = discretionaryNow.subtract(pessimisticVariableRemaining);
        BigDecimal expectedEndBalanceHigh = discretionaryNow.subtract(optimisticVariableRemaining);

        ForecastStatus status;
        if (discretionaryNow.signum() < 0
                || !everyOccurrenceCoverable(committedOccurrences, walletBalance, pessimisticVariableRemaining,
                        input.today(), daysRemaining)) {
            status = ForecastStatus.SHORT;
        } else if (expectedVariableRemaining.compareTo(discretionaryNow) > 0) {
            status = ForecastStatus.TIGHT;
        } else {
            status = ForecastStatus.FINE;
        }

        return new ForecastV2(
                status,
                confidence(baseline.cyclesUsed()),
                historyWeight,
                committed,
                discretionaryNow,
                discretionaryPerDay,
                expectedVariableRemaining,
                pessimisticVariableRemaining,
                optimisticVariableRemaining,
                expectedEndBalanceTypical,
                expectedEndBalanceLow,
                expectedEndBalanceHigh,
                pace,
                baseline);
    }

    /** {@code w × historical + (1 − w) × paceProjection × paceFactor}; the historical term vanishes with {@code w == 0}. */
    private static BigDecimal blend(BigDecimal historyWeight, BigDecimal historical, BigDecimal paceProjection,
                                    BigDecimal paceFactor) {
        BigDecimal paceTerm = BigDecimal.ONE.subtract(historyWeight).multiply(paceProjection).multiply(paceFactor);
        if (historyWeight.signum() == 0) {
            return money(paceTerm);
        }
        Objects.requireNonNull(historical, "historical baseline value is required when the history weight is positive");
        return money(historyWeight.multiply(historical).add(paceTerm));
    }

    /**
     * Walks the committed occurrences in due-date order and checks that each one is still covered when it
     * becomes due — the canonical Stage 4.5 affordability test (decision 2026-09-16):
     * <pre>
     * Σ committed due through o (o itself included) + pessimistic × daysUntil(o)/R  ≤  walletBalance
     * </pre>
     * The obligation being checked is counted exactly once, inside the cumulative sum. The plan's shorthand
     * {@code o.amount > walletBalance − Σ committed due ≤ o.dueDate − prorated} is the same test with a sum that
     * <em>excludes</em> {@code o}; subtracting {@code o} from the cash and then comparing {@code o} again would
     * double-count it and is deliberately not what this method does.
     * The pessimistic variable spend is prorated linearly to the due date; an occurrence due today (or already
     * overdue) gets {@code daysUntil = 0}, i.e. it must fit into the balance after every commitment due through
     * today with no room for further variable spend. {@code daysUntil} is clamped to {@code [0, R]}; with
     * {@code R == 0} nothing is prorated and no division happens.
     */
    private static boolean everyOccurrenceCoverable(List<CommittedOccurrence> committedByDueDate,
                                                    BigDecimal walletBalance,
                                                    BigDecimal pessimisticVariableRemaining,
                                                    LocalDate today,
                                                    int daysRemaining) {
        BigDecimal cumulativeCommitted = BigDecimal.ZERO;
        for (CommittedOccurrence occurrence : committedByDueDate) {
            cumulativeCommitted = cumulativeCommitted.add(money(occurrence.expectedAmount()));
            BigDecimal proratedVariable = proratedVariable(pessimisticVariableRemaining,
                    ChronoUnit.DAYS.between(today, occurrence.dueDate()), daysRemaining);
            BigDecimal available = walletBalance.subtract(cumulativeCommitted).subtract(proratedVariable);
            if (available.signum() < 0) {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal proratedVariable(BigDecimal variableRemaining, long daysUntilDue, int daysRemaining) {
        if (daysRemaining <= 0 || daysUntilDue <= 0) {
            return money(BigDecimal.ZERO);
        }
        long days = Math.min(daysUntilDue, daysRemaining);
        return variableRemaining.multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(daysRemaining), SCALE, ROUNDING);
    }

    static ForecastConfidence confidence(int cyclesUsed) {
        if (cyclesUsed >= FULL_HISTORY_CYCLES) {
            return ForecastConfidence.HIGH;
        }
        return cyclesUsed >= 1 ? ForecastConfidence.MEDIUM : ForecastConfidence.LOW;
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

    /**
     * One fixed-payment occurrence as the Stage 3 tile classified it. Only {@link #committed()} rows enter the
     * forecast; the others are accepted so a caller can hand over the tile's rows unfiltered.
     */
    public record CommittedOccurrence(LocalDate dueDate, BigDecimal expectedAmount, FixedOccurrenceBucket bucket) {

        public CommittedOccurrence {
            Objects.requireNonNull(dueDate, "dueDate");
            Objects.requireNonNull(expectedAmount, "expectedAmount");
            Objects.requireNonNull(bucket, "bucket — an occurrence without cycle context cannot be classified");
        }

        public static CommittedOccurrence from(FixedOccurrenceRowDto row) {
            return new CommittedOccurrence(row.dueDate(), row.expectedAmount(), row.bucket());
        }

        /** Unpaid and due inside the committed window: OVERDUE, DUE_SOON or LATER_THIS_CYCLE. */
        public boolean committed() {
            return bucket == FixedOccurrenceBucket.OVERDUE
                    || bucket == FixedOccurrenceBucket.DUE_SOON
                    || bucket == FixedOccurrenceBucket.LATER_THIS_CYCLE;
        }
    }

    /**
     * @param walletBalance   current balance of the salary wallet
     * @param today           the viewing day (reference for {@code daysUntil} of each occurrence)
     * @param dayIndex        {@code d} — 1-based day index of {@code today} in the open cycle; must equal
     *                        {@code currentPace.daysElapsed()} (checked by {@link #forecast})
     * @param cycleLengthDays {@code D} — {@code end − start + 1}
     * @param daysRemaining   {@code R} — days after {@code today} through {@code end}, {@code ≥ 0}
     * @param occurrences     the Stage 3 tile rows of the cycle (any bucket; see {@link CommittedOccurrence#committed()})
     * @param currentPace     {@link #currentPace} over the pace-eligible series {@code start..today}
     * @param baseline        Stage 4.3 baseline for the same {@code d} / {@code R}
     */
    public record ForecastInput(
            BigDecimal walletBalance,
            LocalDate today,
            int dayIndex,
            int cycleLengthDays,
            int daysRemaining,
            List<CommittedOccurrence> occurrences,
            CurrentPace currentPace,
            HistoricalBaseline baseline) {

        public ForecastInput {
            Objects.requireNonNull(walletBalance, "walletBalance");
            Objects.requireNonNull(today, "today");
            Objects.requireNonNull(currentPace, "currentPace");
            Objects.requireNonNull(baseline, "baseline");
            occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
            if (cycleLengthDays < 1) {
                throw new IllegalArgumentException("cycleLengthDays must be positive: " + cycleLengthDays);
            }
            if (dayIndex < 0) {
                throw new IllegalArgumentException("dayIndex must not be negative: " + dayIndex);
            }
            if (daysRemaining < 0) {
                throw new IllegalArgumentException("daysRemaining must not be negative: " + daysRemaining);
            }
        }

        /** The same input with another current pace — used by the one-off impact recomputation (Stage 4.6). */
        public ForecastInput withCurrentPace(CurrentPace pace) {
            return new ForecastInput(walletBalance, today, dayIndex, cycleLengthDays, daysRemaining, occurrences,
                    pace, baseline);
        }
    }

    /**
     * Stage 4.5 result. {@code pessimisticVariableRemaining} (p75 / pace × 1.25) drives
     * {@code expectedEndBalanceLow}; {@code optimisticVariableRemaining} (p25 / pace × 0.75) drives
     * {@code expectedEndBalanceHigh}.
     */
    public record ForecastV2(
            ForecastStatus status,
            ForecastConfidence confidence,
            BigDecimal historyWeight,
            BigDecimal committed,
            BigDecimal discretionaryNow,
            BigDecimal discretionaryPerDay,
            BigDecimal expectedVariableRemaining,
            BigDecimal pessimisticVariableRemaining,
            BigDecimal optimisticVariableRemaining,
            BigDecimal expectedEndBalanceTypical,
            BigDecimal expectedEndBalanceLow,
            BigDecimal expectedEndBalanceHigh,
            CurrentPace currentPace,
            HistoricalBaseline baseline) {
    }
}
