package com.mikeshaggy.backend.analytics.breakdown;

import com.mikeshaggy.backend.transaction.domain.Importance;

import java.math.BigDecimal;

public record ImportanceBreakdownItemDto(
        Importance importance,
        BigDecimal amount,
        BigDecimal share,
        Long count
) {
}
