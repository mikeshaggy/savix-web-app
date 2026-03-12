package com.mikeshaggy.backend.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WalletCreateRequest(
        @NotNull(message = "Name is required")
        @Size(max = 50, message = "Name must not exceed 50 characters")
        String name,

        BigDecimal balance
) {}
