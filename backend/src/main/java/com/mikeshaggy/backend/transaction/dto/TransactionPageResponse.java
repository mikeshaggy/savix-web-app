package com.mikeshaggy.backend.transaction.dto;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {}
