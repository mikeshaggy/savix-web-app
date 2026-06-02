package com.mikeshaggy.backend.fund.dto;

public record FundArchiveRequest(
        Boolean returnRemainingBalance,
        Integer returnToWalletId
) {}
