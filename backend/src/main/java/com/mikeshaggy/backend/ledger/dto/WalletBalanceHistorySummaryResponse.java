package com.mikeshaggy.backend.ledger.dto;

import java.math.BigDecimal;

public record WalletBalanceHistorySummaryResponse(
        BigDecimal latestBalance,
        BigDecimal highestBalance,
        BigDecimal lowestBalance,
        Integer entriesCount,
        BigDecimal netChange
) {}