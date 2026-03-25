package com.mikeshaggy.backend.ledger.dto;

public record WalletBalanceHistoryTimelinePaginationResponse(
        int currentPage,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {}