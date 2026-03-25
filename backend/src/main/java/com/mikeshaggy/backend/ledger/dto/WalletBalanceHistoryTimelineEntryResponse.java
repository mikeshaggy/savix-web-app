package com.mikeshaggy.backend.ledger.dto;

import com.mikeshaggy.backend.ledger.domain.SourceType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WalletBalanceHistoryTimelineEntryResponse(
        Long id,
        LocalDate entryDate,
        BigDecimal amountSigned,
        BigDecimal balanceAfter,
        SourceType sourceType,
        Long sourceId,
        String sourceLabel,
        String sourceReference
) {}