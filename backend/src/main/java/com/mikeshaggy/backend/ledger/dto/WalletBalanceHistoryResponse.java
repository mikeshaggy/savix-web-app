package com.mikeshaggy.backend.ledger.dto;

import java.math.BigDecimal;
import java.util.List;

public record WalletBalanceHistoryResponse(
        Integer walletId,
        String walletName,
        BigDecimal currentBalance,
        WalletBalanceHistorySummaryResponse summary,
        List<WalletBalanceHistoryChartPointResponse> chart,
        List<WalletBalanceHistoryTimelineGroupResponse> timeline,
        WalletBalanceHistoryTimelinePaginationResponse timelinePagination
) {}