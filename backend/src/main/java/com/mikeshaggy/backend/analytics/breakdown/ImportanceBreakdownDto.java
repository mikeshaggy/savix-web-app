package com.mikeshaggy.backend.analytics.breakdown;

import com.mikeshaggy.backend.common.period.PeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ImportanceBreakdownDto(
        PeriodType periodType,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalExpenses,
        List<ImportanceBreakdownItemDto> breakdown
) {
}
