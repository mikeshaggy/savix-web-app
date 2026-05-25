package com.mikeshaggy.backend.analytics.aggregation;

import java.math.BigDecimal;
import java.util.List;

public record CategoryAggregationResult(
        BigDecimal total,
        List<CategoryAggregation> categories
) {
}
