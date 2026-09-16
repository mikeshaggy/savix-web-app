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
 *
 * <p><b>Obligation view, not cash flow (Stage 3.5 decision B).</b> {@code paidAmount} sums the PAID
 * occurrences whose <em>due date</em> falls in the cycle, whatever date the linked transaction carries. The
 * forecast's {@code SpendingProjectionDto.linkedFixedExpensesToDate} is a different metric: linked transactions
 * <em>dated</em> in {@code [cycle start, today]}. They coincide when every obligation is paid inside its own
 * cycle and diverge when one is paid early (before the cycle starts) or late (after the next cycle begins);
 * neither is corrected to match the other.
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
