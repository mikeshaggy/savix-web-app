package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardFixedPaymentsDto(
        BigDecimal plannedAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        BigDecimal overdueAmount,
        int paidCount,
        int totalCount,
        DashboardFixedPaymentOccurrenceDto nextPendingOccurrence,
        List<DashboardFixedPaymentOccurrenceDto> upcomingOccurrences,
        BigDecimal balanceAfterRemainingFixedPayments,
        boolean atRisk,
        BigDecimal shortfallAmount
) {
}
