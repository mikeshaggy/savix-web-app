package com.mikeshaggy.backend.wallet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletDTO(
        Integer id,

        @NotNull(message = "User ID is required")
        UUID userId,

        @NotNull(message = "Name is required")
        @Size(max = 50, message = "Name must not exceed 50 characters")
        String name,

        BigDecimal balance,

        Instant createdAt
) {}
