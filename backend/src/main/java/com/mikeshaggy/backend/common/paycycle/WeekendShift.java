package com.mikeshaggy.backend.common.paycycle;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * How a payday that falls on a weekend is moved. No holiday calendar — weekends only.
 */
public enum WeekendShift {
    NONE,
    PREVIOUS_BUSINESS_DAY,
    NEXT_BUSINESS_DAY;

    public LocalDate apply(LocalDate date) {
        return switch (this) {
            case NONE -> date;
            case PREVIOUS_BUSINESS_DAY -> previousBusinessDay(date);
            case NEXT_BUSINESS_DAY -> nextBusinessDay(date);
        };
    }

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /** The date itself when it is a business day, otherwise the closest earlier business day. */
    public static LocalDate previousBusinessDay(LocalDate date) {
        LocalDate result = date;
        while (isWeekend(result)) {
            result = result.minusDays(1);
        }
        return result;
    }

    /** The date itself when it is a business day, otherwise the closest later business day. */
    public static LocalDate nextBusinessDay(LocalDate date) {
        LocalDate result = date;
        while (isWeekend(result)) {
            result = result.plusDays(1);
        }
        return result;
    }
}
