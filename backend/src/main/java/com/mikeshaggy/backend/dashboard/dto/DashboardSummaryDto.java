package com.mikeshaggy.backend.dashboard.dto;

import java.util.List;

public record DashboardSummaryDto(
        Integer walletId,
        String walletName,
        DashboardPeriodDto period,
        DashboardCycleHealthDto cycleHealth,
        DashboardKpisDto kpis,
        DashboardFixedPaymentsDto fixedPayments,
        List<DashboardInsightDto> insights,
        List<DashboardCategoryPressureItemDto> categoryPressure,
        DashboardPreviousCyclePreviewDto previousCyclePreview
) {
}
