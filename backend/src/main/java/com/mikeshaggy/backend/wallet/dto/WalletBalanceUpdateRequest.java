package com.mikeshaggy.backend.wallet.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WalletBalanceUpdateRequest(
        @NotNull(message = "New balance is required")
        BigDecimal newBalance,

        LocalDate effectiveDate
) {}
