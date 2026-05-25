package com.mikeshaggy.backend.analytics.aggregation;

import java.math.BigDecimal;

public record CategoryAggregation(
        Integer categoryId,
        String name,
        String emoji,
        BigDecimal amount,
        BigDecimal share,
        Long transactionCount
) {
}
