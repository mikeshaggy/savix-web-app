package com.mikeshaggy.backend.fund.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundUpdateRequest(
        @Size(max = 100, message = "Fund name must be 100 characters or less")
        String name,

        String description,

        @DecimalMin(value = "0.01", message = "Target amount must be greater than 0")
        BigDecimal targetAmount,

        @Size(max = 16, message = "Fund emoji must be 16 characters or less")
        String emoji,

        String color,

        LocalDate deadlineDate,

        Integer sourceWalletId
) {}
