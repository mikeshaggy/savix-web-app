package com.mikeshaggy.backend.fund.dto;

import java.util.List;

public record FundMovementPageResponse(
        List<FundMovementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {}
