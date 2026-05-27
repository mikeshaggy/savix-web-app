package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;

public record DashboardMoneyKpiDto(
        BigDecimal amount,
        BigDecimal deltaAmount,
        BigDecimal deltaPercent
) {
}
