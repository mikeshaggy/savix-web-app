package com.mikeshaggy.backend.user.dto;

import com.mikeshaggy.backend.common.paycycle.WeekendShift;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PaydayRuleRequest(
        @NotNull(message = "Day of month is required")
        @Min(value = 1, message = "Day of month must be between 1 and 31")
        @Max(value = 31, message = "Day of month must be between 1 and 31")
        Integer dayOfMonth,
        /** Defaults to {@link WeekendShift#PREVIOUS_BUSINESS_DAY} when omitted (matches the DB default). */
        WeekendShift weekendShift
) {
    public WeekendShift weekendShiftOrDefault() {
        return weekendShift != null ? weekendShift : WeekendShift.PREVIOUS_BUSINESS_DAY;
    }
}
