package com.mikeshaggy.backend.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundSummaryItemDto(
        Long id,
        String name,
        String icon,
        String color,
        BigDecimal progressPercent,
        BigDecimal currentAmount,
        BigDecimal targetAmount,
        boolean isTargetReached,
        LocalDate deadlineDate,
        Long daysUntilDeadline
) {}
