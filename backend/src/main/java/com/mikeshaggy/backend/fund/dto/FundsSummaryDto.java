package com.mikeshaggy.backend.fund.dto;

import java.math.BigDecimal;
import java.util.List;

public record FundsSummaryDto(
        int activeFundsCount,
        BigDecimal totalSaved,
        BigDecimal totalTarget,
        BigDecimal overallProgressPercent,
        List<FundSummaryItemDto> topFunds,
        List<FundSummaryItemDto> nearDeadlineFunds
) {}
