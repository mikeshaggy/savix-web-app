package com.mikeshaggy.backend.fixedpayment.dto;

import jakarta.validation.constraints.NotNull;

public record LinkOccurrenceRequest(
        @NotNull(message = "Transaction ID is required")
        Long transactionId
) {}
