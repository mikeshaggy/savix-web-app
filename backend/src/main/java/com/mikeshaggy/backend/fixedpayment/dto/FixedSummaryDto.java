package com.mikeshaggy.backend.fixedpayment.dto;

import java.math.BigDecimal;

public record FixedSummaryDto(
        BigDecimal plannedAmount,
        int plannedCount,
        BigDecimal paidAmount,
        int paidCount,
        BigDecimal remainingAmount,
        int remainingCount,
        BigDecimal overdueAmount,
        int overdueCount,
        double fixedRatio
) {}
