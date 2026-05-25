package com.mikeshaggy.backend.analytics.insight;

import java.math.BigDecimal;

public record InsightDto(
        InsightType type,
        InsightSeverity severity,
        String title,
        String description,
        Integer relatedCategoryId,
        BigDecimal relatedAmount
) {}
