package com.mikeshaggy.backend.analytics.cyclecomparison;

import java.util.List;

public record CycleComparisonHighlightsDto(
        List<CycleComparisonCategoryDto> largestIncrease,
        List<CycleComparisonCategoryDto> largestDecrease
) {
}
