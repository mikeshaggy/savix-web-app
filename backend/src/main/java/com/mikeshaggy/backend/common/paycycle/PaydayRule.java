package com.mikeshaggy.backend.common.paycycle;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * "Salary arrives on day {@code dayOfMonth}, shifted by {@code weekendShift} when that day is a weekend."
 * The day-of-month is clamped to the length of the target month (31 → 30 in a 30-day month).
 */
public record PaydayRule(int dayOfMonth, WeekendShift weekendShift, PaydayRuleSource source) {

    public PaydayRule {
        if (dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException("dayOfMonth must be between 1 and 31, was " + dayOfMonth);
        }
        Objects.requireNonNull(weekendShift, "weekendShift");
        Objects.requireNonNull(source, "source");
    }

    public static PaydayRule configured(int dayOfMonth, WeekendShift weekendShift) {
        return new PaydayRule(dayOfMonth, weekendShift, PaydayRuleSource.CONFIGURED);
    }

    /** The unshifted payday in the given month, with the day-of-month clamped to the month length. */
    public LocalDate nominalDateIn(YearMonth month) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
    }
}
