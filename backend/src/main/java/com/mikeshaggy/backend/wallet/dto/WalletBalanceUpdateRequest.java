package com.mikeshaggy.backend.wallet.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WalletBalanceUpdateRequest(
        @NotNull(message = "New balance is required")
        BigDecimal newBalance
) {}
