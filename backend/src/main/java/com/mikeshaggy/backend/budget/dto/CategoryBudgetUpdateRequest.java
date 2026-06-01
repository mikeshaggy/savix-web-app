package com.mikeshaggy.backend.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CategoryBudgetUpdateRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        BigDecimal amount,

        @Min(value = 1, message = "Warning threshold must be between 1 and 100")
        @Max(value = 100, message = "Warning threshold must be between 1 and 100")
        Integer warningThresholdPercent
) {}
