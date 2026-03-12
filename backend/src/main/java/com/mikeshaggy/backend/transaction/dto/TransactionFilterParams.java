package com.mikeshaggy.backend.transaction.dto;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.domain.Importance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TransactionFilterParams(
        UUID userId,
        Integer walletId,
        int page,
        int size,
        List<CategoryType> types,
        List<Integer> categoryIds,
        List<Importance> importances,
        LocalDate startDate,
        LocalDate endDate,
        String q,
        String sort
) {}
