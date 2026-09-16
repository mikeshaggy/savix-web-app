package com.mikeshaggy.backend.fixedpayment.dto;

import com.mikeshaggy.backend.common.paycycle.CycleState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The fixed-payments tile for one committed window.
 * <p>
 * {@code periodEnd} is the inclusive end of the committed window: {@code expectedNextAnchor − 1} for an open
 * cycle (the salary date itself belongs to the next cycle), today while the cycle is awaiting its salary.
 * {@code expectedPaydayDate} and {@code cycleState} are pay-cycle metadata and stay {@code null} for
 * reporting periods and non-salary wallets.
 */
public record FixedTransactionsTileDto(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate billingEndDate,
        LocalDate expectedPaydayDate,
        CycleState cycleState,
        FixedSummaryDto summary,
        FixedProgressDto progress,
        BigDecimal currentBalance,
        BigDecimal balanceAfterFixed,
        RiskIndicatorDto riskIndicator,
        List<FixedOccurrenceRowDto> overdue,
        List<FixedOccurrenceRowDto> upcoming,
        List<FixedOccurrenceRowDto> paid
) {}
