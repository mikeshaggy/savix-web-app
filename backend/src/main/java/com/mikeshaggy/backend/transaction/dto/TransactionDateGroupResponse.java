package com.mikeshaggy.backend.transaction.dto;

import java.time.LocalDate;
import java.util.List;

public record TransactionDateGroupResponse(
        LocalDate date,
        List<TransactionResponse> transactions
) {}
