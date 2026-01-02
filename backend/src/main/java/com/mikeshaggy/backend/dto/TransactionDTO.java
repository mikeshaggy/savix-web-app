package com.mikeshaggy.backend.dto;

import com.mikeshaggy.backend.domain.transaction.Cycle;
import com.mikeshaggy.backend.domain.transaction.Importance;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionDTO(
        Long id,

        @NotNull(message = "Wallet ID is required")
        Integer walletId,

        @NotNull(message = "Category ID is required")
        Integer categoryId,

        @NotBlank(message = "Title is required")
        @Size(max = 50, message = "Title must not exceed 50 characters")
        String title,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "Transaction date is required")
        LocalDate transactionDate,

        String notes,

        Importance importance,

        @NotNull(message = "Cycle is required")
        Cycle cycle,

        Instant createdAt
) {}
