package com.mikeshaggy.backend.wallet.dto;

import com.mikeshaggy.backend.wallet.domain.Wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletResponse(
        Integer id,
        String name,
        BigDecimal balance,
        Instant createdAt
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getName(),
                wallet.getBalance(),
                wallet.getCreatedAt()
        );
    }
}
