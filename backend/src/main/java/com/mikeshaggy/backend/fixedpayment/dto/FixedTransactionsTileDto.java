package com.mikeshaggy.backend.fixedpayment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FixedTransactionsTileDto(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate billingEndDate,
        FixedSummaryDto summary,
        FixedProgressDto progress,
        BigDecimal currentBalance,
        BigDecimal balanceAfterFixed,
        RiskIndicatorDto riskIndicator,
        List<FixedOccurrenceRowDto> overdue,
        List<FixedOccurrenceRowDto> upcoming,
        List<FixedOccurrenceRowDto> paid
) {}
