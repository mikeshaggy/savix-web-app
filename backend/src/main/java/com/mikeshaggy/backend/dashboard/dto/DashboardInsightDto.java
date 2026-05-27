package com.mikeshaggy.backend.dashboard.dto;

import com.mikeshaggy.backend.analytics.insight.InsightSeverity;
import com.mikeshaggy.backend.analytics.insight.InsightType;

import java.math.BigDecimal;

public record DashboardInsightDto(
        InsightType type,
        InsightSeverity severity,
        String title,
        String description,
        BigDecimal relatedAmount
) {
}
