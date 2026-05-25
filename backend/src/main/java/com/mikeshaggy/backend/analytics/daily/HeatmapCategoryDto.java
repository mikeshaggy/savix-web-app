package com.mikeshaggy.backend.analytics.daily;

import java.math.BigDecimal;

public record HeatmapCategoryDto(
        Integer categoryId,
        String categoryName,
        String emoji,
        BigDecimal amount,
        long transactions
) {
}
