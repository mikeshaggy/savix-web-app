package com.mikeshaggy.backend.fund.dto;

import java.util.List;

/**
 * Paginated fund movement history. Shape mirrors the project's existing page
 * response convention (see {@code TransactionPageResponse}).
 */
public record FundMovementPageResponse(
        List<FundMovementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {}
