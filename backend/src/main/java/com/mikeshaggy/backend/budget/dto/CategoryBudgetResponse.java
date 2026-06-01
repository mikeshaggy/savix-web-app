package com.mikeshaggy.backend.budget.dto;

import com.mikeshaggy.backend.budget.domain.CategoryBudget;
import com.mikeshaggy.backend.category.domain.CategoryType;

import java.math.BigDecimal;
import java.time.Instant;

public record CategoryBudgetResponse(
        Integer id,
        Integer categoryId,
        String categoryName,
        String categoryEmoji,
        CategoryType categoryType,
        Integer walletId,
        BigDecimal amount,
        Integer warningThresholdPercent,
        boolean active,
        Instant createdAt
) {
    public static CategoryBudgetResponse from(CategoryBudget budget) {
        return new CategoryBudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getCategory().getEmoji(),
                budget.getCategory().getType(),
                budget.getWallet().getId(),
                budget.getAmount(),
                budget.getWarningThresholdPercent(),
                budget.isActive(),
                budget.getCreatedAt()
        );
    }
}
