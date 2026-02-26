package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;

public record PercentageChangeDto(
        BigDecimal percentage,
        boolean isPositive
) {}
