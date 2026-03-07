package com.mikeshaggy.backend.fixedpayment.dto;

import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateFixedPaymentRequest(
        @NotBlank
        @Size(max = 50)
        String title,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount,

        @NotNull
        LocalDate anchorDate,

        @NotNull
        Cycle cycle,

        String notes,

        LocalDate activeTo
) {}
