package com.mikeshaggy.backend.analytics.cyclecomparison;

import com.mikeshaggy.backend.analytics.insight.InsightDto;

import java.time.LocalDate;
import java.util.List;

public record CycleComparisonResponseDto(
        Integer walletId,
        LocalDate asOfDate,
        CycleComparisonCycleDto currentCycle,
        CycleComparisonBaselineDto baseline,
        CycleComparisonSummaryDto summary,
        List<CycleComparisonCategoryDto> categories,
        CycleComparisonSeriesDto series,
        CycleComparisonHighlightsDto highlights,
        List<InsightDto> insights
) {
}
