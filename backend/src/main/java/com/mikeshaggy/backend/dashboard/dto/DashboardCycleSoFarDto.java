package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;

/**
 * Stage 6.3 ({@code dashboard-v2}) — what the salary wallet's current pay cycle has spent so far, against what is
 * typical by the same cycle day.
 *
 * <p>{@code variableSpent} is pace-eligible variable spend (unlinked expenses, rows excluded from the pace left out)
 * through the dashboard cutoff — the same series the Stage 4 baseline and {@code forecast.variableToDate} read.
 * {@code fixedSpent} is the ledger's fixed-linked spend over the same window (pace flags do not apply to linked rows).
 * An unlinked expense excluded from the pace is in neither figure; it stays in the reporting total
 * {@code kpis.expenses}.
 *
 * <p>{@code typicalVariableByToday} is the Stage 4 historical baseline at today's cycle day: the median cumulative
 * pace-eligible variable spend by that day over the baseline cycles (the Forecast trajectory's typical line);
 * {@code null} without history. {@code deltaVsTypical = variableSpent − typicalVariableByToday}, {@code null} when
 * the typical is.
 */
public record DashboardCycleSoFarDto(
        BigDecimal variableSpent,
        BigDecimal fixedSpent,
        BigDecimal typicalVariableByToday,
        BigDecimal deltaVsTypical
) {
}
