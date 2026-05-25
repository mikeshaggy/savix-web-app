package com.mikeshaggy.backend.analytics.daily;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HeatmapResponseDto(
        LocalDate startDate,
        LocalDate endDate,
        List<HeatmapDayDto> days,
        BigDecimal maxDayTotal
) {
}
