package com.mikeshaggy.backend.analytics.daily;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HeatmapDayDto(
        LocalDate date,
        BigDecimal total,
        long transactions,
        List<HeatmapCategoryDto> categories
) {
}
