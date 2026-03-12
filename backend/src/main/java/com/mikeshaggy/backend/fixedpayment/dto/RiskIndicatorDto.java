package com.mikeshaggy.backend.fixedpayment.dto;

import java.math.BigDecimal;

public record RiskIndicatorDto(
        boolean isAtRisk,
        BigDecimal shortfallAmount
) {}
