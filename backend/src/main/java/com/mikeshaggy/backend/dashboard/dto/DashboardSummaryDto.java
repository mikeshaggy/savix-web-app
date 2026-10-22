package com.mikeshaggy.backend.dashboard.dto;

import java.util.List;

/**
 * The dashboard summary. With {@code dashboard-v2} off the response is the legacy shape and {@code cycleSoFar} /
 * {@code savings} are {@code null}. With the flag on (Stage 6.3) {@code cycleSoFar} and {@code savings} are populated
 * where applicable, {@code categoryPressure} and {@code previousCyclePreview} are {@code null} and the KPI deltas are
 * {@code null} (the fields themselves are deleted in §9); {@code insights} stays until Stage 7.
 */
public record DashboardSummaryDto(
        Integer walletId,
        String walletName,
        DashboardPeriodDto period,
        DashboardCycleHealthDto cycleHealth,
        DashboardKpisDto kpis,
        DashboardFixedPaymentsDto fixedPayments,
        List<DashboardInsightDto> insights,
        List<DashboardCategoryPressureItemDto> categoryPressure,
        DashboardPreviousCyclePreviewDto previousCyclePreview,
        DashboardCycleSoFarDto cycleSoFar,
        DashboardSavingsDto savings
) {
    /** The legacy shape: no Stage 6.3 sections. */
    public DashboardSummaryDto(Integer walletId,
                               String walletName,
                               DashboardPeriodDto period,
                               DashboardCycleHealthDto cycleHealth,
                               DashboardKpisDto kpis,
                               DashboardFixedPaymentsDto fixedPayments,
                               List<DashboardInsightDto> insights,
                               List<DashboardCategoryPressureItemDto> categoryPressure,
                               DashboardPreviousCyclePreviewDto previousCyclePreview) {
        this(walletId, walletName, period, cycleHealth, kpis, fixedPayments, insights, categoryPressure,
                previousCyclePreview, null, null);
    }
}
