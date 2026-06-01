package com.mikeshaggy.backend.budget.dto;

import java.time.LocalDate;
import java.util.List;

public record BudgetUsageResponse(
        Integer walletId,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<BudgetUsageItemDto> budgets
) {}
