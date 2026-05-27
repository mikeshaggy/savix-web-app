package com.mikeshaggy.backend.common.period;

import java.time.LocalDate;

public record InclusiveDateRange(LocalDate startDate, LocalDate endDate) {

    public int days() {
        return daysBetween(startDate, endDate);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public LocalDate clampEnd(LocalDate requestedEndDate) {
        return requestedEndDate.isAfter(endDate) ? endDate : requestedEndDate;
    }

    public static int daysBetween(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return 0;
        }
        return (int) (to.toEpochDay() - from.toEpochDay() + 1);
    }
}
