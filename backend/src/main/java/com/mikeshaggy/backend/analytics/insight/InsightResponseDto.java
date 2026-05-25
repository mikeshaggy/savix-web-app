package com.mikeshaggy.backend.analytics.insight;

import java.time.LocalDate;
import java.util.List;

public record InsightResponseDto(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<InsightDto> insights
) {}
