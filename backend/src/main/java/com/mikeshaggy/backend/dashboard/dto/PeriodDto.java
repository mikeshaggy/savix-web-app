package com.mikeshaggy.backend.dashboard.dto;

import java.time.LocalDate;

public record PeriodDto(
        LocalDate startDate,
        LocalDate endDate,
        LocalDate billingEndDate,
        PeriodType periodType
) {}
