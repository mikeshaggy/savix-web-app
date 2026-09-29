package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleContribution;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One closed cycle behind the historical baseline (Stage 4.3), as exposed by {@link ForecastV2Dto}.
 *
 * <p>{@code variableTotal} is the cycle's whole pace-eligible variable spend; {@code remainingFromDay} the part
 * after the current day index; {@code normalised} that part scaled to the current remaining horizon —
 * {@code null} (and the cycle unused) when the cycle was not longer than the current day index.
 */
public record BaselineCycleDto(
        LocalDate start,
        LocalDate end,
        int lengthDays,
        BigDecimal variableTotal,
        BigDecimal remainingFromDay,
        BigDecimal normalised
) {
    static BaselineCycleDto from(CycleContribution contribution) {
        return new BaselineCycleDto(
                contribution.cycle().start(),
                contribution.cycle().end(),
                contribution.cycleLength(),
                contribution.variableTotal(),
                contribution.remaining(),
                contribution.remainingNormalized());
    }
}
