package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;

public record SummaryDto(
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal saved,
        BigDecimal savingsRate,
        PercentageChangeDto incomeChange,
        PercentageChangeDto expensesChange,
        PercentageChangeDto savedChange
) {}
