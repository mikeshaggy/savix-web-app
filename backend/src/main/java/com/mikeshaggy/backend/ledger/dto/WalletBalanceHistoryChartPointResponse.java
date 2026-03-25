package com.mikeshaggy.backend.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WalletBalanceHistoryChartPointResponse(
        LocalDate date,
        BigDecimal closingBalance,
        BigDecimal netChange
) {}