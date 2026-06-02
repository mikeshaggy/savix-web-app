package com.mikeshaggy.backend.fund.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundWithdrawRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "Destination wallet ID is required")
        Integer destinationWalletId,

        @NotNull(message = "Date is required")
        LocalDate date,

        String notes
) {}
