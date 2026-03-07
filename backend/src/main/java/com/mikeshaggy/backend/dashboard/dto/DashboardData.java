package com.mikeshaggy.backend.dashboard.dto;

import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;

import java.util.List;

public record DashboardData(
        PeriodDto period,
        SummaryDto summary,
        List<CategorySpendingDto> topCategories,
        String walletName,
        FixedTransactionsTileDto fixedPaymentsTile
) {}
