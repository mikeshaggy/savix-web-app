package com.mikeshaggy.backend.ledger.dto;

import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.domain.WalletEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record WalletEntryResponse(
        Long id,
        Integer walletId,
        String walletName,
        BigDecimal amountSigned,
        BigDecimal balanceAfter,
        LocalDate entryDate,
        SourceType sourceType,
        Long sourceId,
        Instant createdAt
) {
    public static WalletEntryResponse from(WalletEntry entry) {
        return new WalletEntryResponse(
                entry.getId(),
                entry.getWallet().getId(),
                entry.getWallet().getName(),
                entry.getAmountSigned(),
                entry.getBalanceAfter(),
                entry.getEntryDate(),
                entry.getSourceType(),
                entry.getSourceId(),
                entry.getCreatedAt()
        );
    }
}
