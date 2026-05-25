package com.mikeshaggy.backend.analytics.aggregation;

import java.math.BigDecimal;

public record CategoryComparisonAggregation(
        CategoryAggregation category,
        BigDecimal compareAmount
) {
}
