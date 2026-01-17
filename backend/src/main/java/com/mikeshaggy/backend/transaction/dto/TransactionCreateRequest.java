package com.mikeshaggy.backend.transaction.dto;

import com.mikeshaggy.backend.transaction.domain.Cycle;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionCreateRequest(
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
        Cycle cycle
) {
    public Transaction toEntity() {
        return Transaction.builder()
                .title(title)
                .amount(amount)
                .transactionDate(transactionDate)
                .notes(notes)
                .importance(importance)
                .cycle(cycle)
                .build();
    }
}
