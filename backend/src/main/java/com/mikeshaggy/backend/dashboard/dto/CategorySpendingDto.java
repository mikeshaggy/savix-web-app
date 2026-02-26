package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;

public record CategorySpendingDto(
        String categoryName,
        BigDecimal amount,
        BigDecimal percentageOfTotal,
        PercentageChangeDto change
) {}
