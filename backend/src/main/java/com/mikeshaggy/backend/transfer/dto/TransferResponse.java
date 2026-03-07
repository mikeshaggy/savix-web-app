package com.mikeshaggy.backend.transfer.dto;

import com.mikeshaggy.backend.transfer.domain.Transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransferResponse(
        Long id,
        Integer fromWalletId,
        String fromWalletName,
        Integer toWalletId,
        String toWalletName,
        BigDecimal amount,
        LocalDate transferDate,
        String notes,
        Instant createdAt
) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getFromWallet().getId(),
                transfer.getFromWallet().getName(),
                transfer.getToWallet().getId(),
                transfer.getToWallet().getName(),
                transfer.getAmount(),
                transfer.getTransferDate(),
                transfer.getNotes(),
                transfer.getCreatedAt()
        );
    }
}
