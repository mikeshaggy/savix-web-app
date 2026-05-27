package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardPreviousCyclePreviewDto(
        BigDecimal expensesDeltaAmount,
        BigDecimal expensesDeltaPercent,
        BigDecimal incomeDeltaAmount,
        BigDecimal incomeDeltaPercent,
        BigDecimal savedDeltaAmount,
        BigDecimal savedDeltaPercent,
        BigDecimal savingsRateDeltaPercentagePoints,
        List<DashboardCategoryPressureItemDto> topChangedCategories
) {
}
