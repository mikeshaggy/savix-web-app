package com.mikeshaggy.backend.transfer.dto;

import com.mikeshaggy.backend.transfer.domain.Transfer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferCreateRequest(
        @NotNull(message = "Source wallet ID is required")
        Integer fromWalletId,

        @NotNull(message = "Destination wallet ID is required")
        Integer toWalletId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "Transfer date is required")
        LocalDate transferDate,

        String notes
) {
    public Transfer toEntity() {
        return Transfer.builder()
                .amount(amount)
                .transferDate(transferDate)
                .notes(notes)
                .build();
    }
}
