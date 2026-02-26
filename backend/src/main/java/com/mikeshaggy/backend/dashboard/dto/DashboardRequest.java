package com.mikeshaggy.backend.dashboard.dto;

public record DashboardRequest(
        Integer walletId,
        String fromDate,
        String toDate,
        PeriodType periodType
) {}
