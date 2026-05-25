package com.mikeshaggy.backend.analytics.comparison;

import java.math.BigDecimal;

public record BaselineMonthDto(
        String month,
        BigDecimal income,
        BigDecimal expenses
) {
}
