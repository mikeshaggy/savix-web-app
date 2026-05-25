package com.mikeshaggy.backend.analytics.comparison;

import java.math.BigDecimal;

public record BaselineDeltaDto(
        BigDecimal percent,
        String display,
        boolean available
) {
}
