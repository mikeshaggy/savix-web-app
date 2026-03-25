package com.mikeshaggy.backend.ledger.dto;

import java.time.LocalDate;
import java.util.List;

public record WalletBalanceHistoryTimelineGroupResponse(
        LocalDate date,
        List<WalletBalanceHistoryTimelineEntryResponse> entries
) {}