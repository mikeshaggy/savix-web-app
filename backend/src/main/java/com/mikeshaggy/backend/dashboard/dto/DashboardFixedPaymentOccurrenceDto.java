package com.mikeshaggy.backend.dashboard.dto;

import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardFixedPaymentOccurrenceDto(
        Long id,
        String name,
        BigDecimal amount,
        LocalDate dueDate,
        OccurrenceStatus status,
        String categoryName,
        Integer categoryId
) {
}
