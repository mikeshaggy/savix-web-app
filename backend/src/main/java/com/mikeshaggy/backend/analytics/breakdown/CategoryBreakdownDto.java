package com.mikeshaggy.backend.analytics.breakdown;

import com.mikeshaggy.backend.dashboard.dto.PeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CategoryBreakdownDto(
        PeriodType periodType,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalExpenses,
        List<CategoryBreakdownItemDto> categories
) {
}
