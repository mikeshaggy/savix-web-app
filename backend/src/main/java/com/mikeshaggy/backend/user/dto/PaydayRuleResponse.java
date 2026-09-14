package com.mikeshaggy.backend.user.dto;

import com.mikeshaggy.backend.common.paycycle.WeekendShift;
import com.mikeshaggy.backend.user.domain.UserPaydayRule;

public record PaydayRuleResponse(
        int dayOfMonth,
        WeekendShift weekendShift
) {
    public static PaydayRuleResponse from(UserPaydayRule rule) {
        return new PaydayRuleResponse(rule.getDayOfMonth(), rule.getWeekendShift());
    }
}
