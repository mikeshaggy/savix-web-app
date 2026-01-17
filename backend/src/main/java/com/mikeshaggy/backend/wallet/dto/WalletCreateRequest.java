package com.mikeshaggy.backend.wallet.dto;

import com.mikeshaggy.backend.wallet.domain.Wallet;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WalletCreateRequest(
        @NotNull(message = "Name is required")
        @Size(max = 50, message = "Name must not exceed 50 characters")
        String name,

        BigDecimal balance
) {
        public Wallet toEntity() {
                return Wallet.builder()
                        .name(name)
                        .balance(balance != null ? balance : BigDecimal.ZERO)
                        .build();
        }
}
