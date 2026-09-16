package com.mikeshaggy.backend.fixedpayment.dto;

import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Cycle-relative grouping of one occurrence (Stage 3.4), computed once on the server so the Fixed Payments
 * page, the dashboard and any other consumer place the same occurrence under the same heading.
 *
 * <p>Evaluated against the committed window of the cycle the row was selected for: {@code asOfDate} is the
 * viewing day, {@code cycleEnd} the inclusive last day of the window ({@code expectedNextAnchor − 1} for an open
 * cycle, today while awaiting the salary). Calendar months play no part.
 */
public enum FixedOccurrenceBucket {
    /** Unpaid and due before {@code asOfDate}, or flagged {@link OccurrenceStatus#OVERDUE} by maintenance. */
    OVERDUE,
    /** Unpaid, due today or within the next {@link #DUE_SOON_DAYS} days. */
    DUE_SOON,
    /** Unpaid, due later than {@link #DUE_SOON_DAYS} days out but no later than {@code cycleEnd}. */
    LATER_THIS_CYCLE,
    /**
     * Unpaid and due after {@code cycleEnd}, i.e. on or after the expected payday: the next cycle's obligation.
     * Never part of the committed set (Stage 3.1); reaches the page only through the display-only
     * {@code FixedTransactionsTileDto.afterPayday} horizon.
     */
    AFTER_PAYDAY,
    /** {@link OccurrenceStatus#PAID}; the row was selected because its due date falls in the cycle. */
    PAID_THIS_CYCLE;

    public static final int DUE_SOON_DAYS = 7;

    public static FixedOccurrenceBucket of(OccurrenceStatus status, LocalDate dueDate, LocalDate asOfDate,
                                           LocalDate cycleEnd) {
        if (status == OccurrenceStatus.PAID) {
            return PAID_THIS_CYCLE;
        }
        if (status == OccurrenceStatus.OVERDUE || dueDate.isBefore(asOfDate)) {
            return OVERDUE;
        }
        if (dueDate.isAfter(cycleEnd)) {
            return AFTER_PAYDAY;
        }
        long daysUntilDue = ChronoUnit.DAYS.between(asOfDate, dueDate);
        return daysUntilDue <= DUE_SOON_DAYS ? DUE_SOON : LATER_THIS_CYCLE;
    }
}
