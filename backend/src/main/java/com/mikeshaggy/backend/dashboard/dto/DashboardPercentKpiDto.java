package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;

public record DashboardPercentKpiDto(
        BigDecimal percent,
        BigDecimal deltaPercentagePoints
) {
}
