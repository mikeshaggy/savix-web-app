package com.mikeshaggy.backend.transfers.dto;

import com.mikeshaggy.backend.transfers.domain.Transfer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferUpdateRequest(
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
    public void applyTo(Transfer transfer) {
        transfer.setAmount(amount);
        transfer.setTransferDate(transferDate);
        transfer.setNotes(notes);
    }
}
