package com.mikeshaggy.backend.analytics.breakdown;

import java.math.BigDecimal;

public record CategoryBreakdownItemDto(
        Integer categoryId,
        String name,
        String emoji,
        BigDecimal amount,
        BigDecimal share,
        Long transactionCount
) {
}
