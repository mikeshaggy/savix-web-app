package com.mikeshaggy.backend.analytics.cyclecomparison;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CycleComparisonPointDto(
        int dayIndex,
        LocalDate date,
        BigDecimal amount
) {
}
