package com.mikeshaggy.backend.fixedpayment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FixedProgressDto(
        int paidCount,
        int totalCount,
        BigDecimal paidPct,
        LocalDate nextDueDate,
        String nextDueName,
        String biggestUpcomingTitle,
        BigDecimal biggestUpcomingAmount,
        int activeFixedCount
) {}
