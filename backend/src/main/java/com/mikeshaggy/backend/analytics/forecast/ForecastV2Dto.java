package com.mikeshaggy.backend.analytics.forecast;

import java.math.BigDecimal;
import java.util.List;

/**
 * Forecast v2 (Stage 4) for the salary wallet's current pay cycle, assembled by {@link ForecastService}.
 *
 * <p>Two shapes exist. For an {@code OPEN} cycle every field is populated and {@code projectionReason} is
 * {@code null}. While the cycle is {@code AWAITING_SALARY} no remaining horizon exists, so only the
 * obligation-side figures are returned — {@code committed}, {@code committedOccurrences} and
 * {@code discretionaryNow} — with {@code status}, {@code confidence} and every pace / history / range field
 * {@code null}, empty lists, and {@code projectionReason = AWAITING_SALARY}. Reporting periods, closed cycles
 * and non-salary wallets have no forecast at all: the enclosing {@code SpendingProjectionDto.forecast} is
 * {@code null}.
 *
 * <p>Money is 2 dp; {@code historyWeight} is a 4 dp ratio; {@code baselineCyclesUsed} counts the cycles that
 * actually entered the median (a listed cycle may be unusable, see {@link BaselineCycleDto#normalised()}).
 *
 * <p>Stage 5 explanation fields (additive, read-only, {@code null} while awaiting salary):
 * {@code variableToDate} = pace-eligible variable spend so far (Σ of the pace series, excluded rows left out);
 * {@code historicalMedianRemaining} = the baseline median of normalised remaining spend (null without history);
 * {@code paceProjection} = {@code trimmedDailyPace × R}; the blend is
 * {@code historyWeight × historicalMedianRemaining + (1 − historyWeight) × paceProjection}.
 * {@code trajectory} carries the cumulative series of the Stage 5.4 chart.
 */
public record ForecastV2Dto(
        ForecastStatus status,
        ForecastConfidence confidence,
        BigDecimal discretionaryNow,
        BigDecimal discretionaryPerDay,
        BigDecimal committed,
        BigDecimal expectedVariableRemaining,
        BigDecimal expectedVariableRemainingLow,
        BigDecimal expectedVariableRemainingHigh,
        BigDecimal expectedEndBalanceTypical,
        BigDecimal expectedEndBalanceLow,
        BigDecimal expectedEndBalanceHigh,
        BigDecimal trimmedDailyPace,
        BigDecimal rawDailyBurnRate,
        BigDecimal historicalTypicalPerDay,
        BigDecimal historyWeight,
        Integer baselineCyclesUsed,
        List<BaselineCycleDto> baselineCycles,
        List<OneOffDto> oneOffs,
        List<CommittedOccurrenceDto> committedOccurrences,
        String projectionReason,
        BigDecimal variableToDate,
        BigDecimal historicalMedianRemaining,
        BigDecimal paceProjection,
        ForecastTrajectoryDto trajectory
) {
    public ForecastV2Dto {
        baselineCycles = baselineCycles == null ? List.of() : List.copyOf(baselineCycles);
        oneOffs = oneOffs == null ? List.of() : List.copyOf(oneOffs);
        committedOccurrences = committedOccurrences == null ? List.of() : List.copyOf(committedOccurrences);
    }

    /** The Stage 4.7 shape, without the Stage 5 explanation fields. */
    public ForecastV2Dto(ForecastStatus status, ForecastConfidence confidence, BigDecimal discretionaryNow,
                         BigDecimal discretionaryPerDay, BigDecimal committed, BigDecimal expectedVariableRemaining,
                         BigDecimal expectedVariableRemainingLow, BigDecimal expectedVariableRemainingHigh,
                         BigDecimal expectedEndBalanceTypical, BigDecimal expectedEndBalanceLow,
                         BigDecimal expectedEndBalanceHigh, BigDecimal trimmedDailyPace, BigDecimal rawDailyBurnRate,
                         BigDecimal historicalTypicalPerDay, BigDecimal historyWeight, Integer baselineCyclesUsed,
                         List<BaselineCycleDto> baselineCycles, List<OneOffDto> oneOffs,
                         List<CommittedOccurrenceDto> committedOccurrences, String projectionReason) {
        this(status, confidence, discretionaryNow, discretionaryPerDay, committed, expectedVariableRemaining,
                expectedVariableRemainingLow, expectedVariableRemainingHigh, expectedEndBalanceTypical,
                expectedEndBalanceLow, expectedEndBalanceHigh, trimmedDailyPace, rawDailyBurnRate,
                historicalTypicalPerDay, historyWeight, baselineCyclesUsed, baselineCycles, oneOffs,
                committedOccurrences, projectionReason, null, null, null, null);
    }
}
