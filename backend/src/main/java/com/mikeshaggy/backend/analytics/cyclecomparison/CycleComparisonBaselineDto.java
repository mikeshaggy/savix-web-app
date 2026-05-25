package com.mikeshaggy.backend.analytics.cyclecomparison;

import java.util.List;

public record CycleComparisonBaselineDto(
        int requestedCycles,
        int cyclesUsed,
        boolean available,
        List<CycleComparisonBaselineCycleDto> cycles
) {
}
