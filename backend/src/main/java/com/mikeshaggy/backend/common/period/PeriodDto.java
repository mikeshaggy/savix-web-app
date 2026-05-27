package com.mikeshaggy.backend.common.period;

import java.time.LocalDate;

public record PeriodDto(
        LocalDate startDate,
        LocalDate endDate,
        LocalDate billingEndDate,
        PeriodType periodType
) {}
