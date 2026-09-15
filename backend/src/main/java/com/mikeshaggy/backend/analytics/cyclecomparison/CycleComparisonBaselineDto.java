package com.mikeshaggy.backend.analytics.cyclecomparison;

import java.util.List;

public record CycleComparisonBaselineDto(
        int requestedCycles,
        int cyclesUsed,
        boolean available,
        /** Closed cycles that exist for this wallet (capped at 12) — the UI caps its baseline options to it. */
        int availableCycles,
        List<CycleComparisonBaselineCycleDto> cycles,
        CycleComparisonBaselineKind kind
) {
}
