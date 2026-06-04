package com.mikeshaggy.backend.fund.dto;

import com.mikeshaggy.backend.fund.domain.FundMovementType;
import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.wallet.domain.Wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record FundMovementResponse(
        Long id,
        Long transferId,
        FundMovementType type,
        BigDecimal amount,
        LocalDate date,
        Integer counterpartyWalletId,
        String counterpartyWalletName,
        String notes,
        Instant createdAt
) {

    public static FundMovementResponse from(Transfer transfer, Integer fundWalletId) {
        boolean isDeposit = transfer.getToWallet().getId().equals(fundWalletId);
        Wallet counterparty = isDeposit ? transfer.getFromWallet() : transfer.getToWallet();

        return new FundMovementResponse(
                transfer.getId(),
                transfer.getId(),
                isDeposit ? FundMovementType.DEPOSIT : FundMovementType.WITHDRAWAL,
                transfer.getAmount(),
                transfer.getTransferDate(),
                counterparty.getId(),
                counterparty.getName(),
                transfer.getNotes(),
                transfer.getCreatedAt()
        );
    }
}
