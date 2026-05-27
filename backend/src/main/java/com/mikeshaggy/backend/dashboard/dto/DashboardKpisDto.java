package com.mikeshaggy.backend.dashboard.dto;

public record DashboardKpisDto(
        DashboardMoneyKpiDto income,
        DashboardMoneyKpiDto expenses,
        DashboardMoneyKpiDto saved,
        DashboardPercentKpiDto savingsRate
) {
}
