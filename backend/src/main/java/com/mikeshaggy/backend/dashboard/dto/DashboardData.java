package com.mikeshaggy.backend.dashboard.dto;

import java.util.List;

public record DashboardData(
        PeriodDto period,
        SummaryDto summary,
        List<CategorySpendingDto> topCategories,
        String walletName
) {}
