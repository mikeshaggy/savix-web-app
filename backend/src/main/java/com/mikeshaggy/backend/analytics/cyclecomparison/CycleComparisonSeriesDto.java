package com.mikeshaggy.backend.analytics.cyclecomparison;

import java.util.List;

public record CycleComparisonSeriesDto(
        List<CycleComparisonPointDto> currentCumulative,
        List<CycleComparisonPointDto> baselineAverageCumulative
) {
}
