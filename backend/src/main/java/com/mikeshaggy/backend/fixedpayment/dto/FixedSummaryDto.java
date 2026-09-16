package com.mikeshaggy.backend.fixedpayment.dto;

import java.math.BigDecimal;

/**
 * Cycle-level fixed-payment totals.
 *
 * <p>{@code paidAmount} is the <em>actual</em> sum of the linked transactions
 * (falling back to the expected amount for a paid occurrence without a recorded
 * amount); {@code plannedPaidAmount} is what those same occurrences were
 * expected to cost. {@code plannedAmount} / {@code remainingAmount} /
 * {@code overdueAmount} remain expected-amount sums.
 */
public record FixedSummaryDto(
        BigDecimal plannedAmount,
        int plannedCount,
        BigDecimal paidAmount,
        int paidCount,
        BigDecimal remainingAmount,
        int remainingCount,
        BigDecimal overdueAmount,
        int overdueCount,
        BigDecimal fixedRatio,
        BigDecimal plannedPaidAmount
) {}
