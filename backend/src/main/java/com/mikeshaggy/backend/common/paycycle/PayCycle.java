package com.mikeshaggy.backend.common.paycycle;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One salary-to-salary cycle of a user, resolved from the designated salary wallet.
 * <p>
 * {@code start} is always an actual salary anchor date. {@code end} is a fact for {@link CycleState#CLOSED}
 * cycles (the day before the next anchor) and {@code expectedNextAnchor − 1} for {@link CycleState#OPEN} /
 * {@link CycleState#AWAITING_SALARY} cycles. {@code expectedNextAnchor} is {@code null} once the cycle is closed.
 */
public record PayCycle(
        UUID userId,
        Integer salaryWalletId,
        LocalDate start,
        LocalDate end,
        LocalDate expectedNextAnchor,
        CycleState state) {

    public PayCycle {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(state, "state");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end " + end + " is before start " + start);
        }
        if (state == CycleState.CLOSED) {
            if (expectedNextAnchor != null) {
                throw new IllegalArgumentException("a CLOSED cycle has no expectedNextAnchor");
            }
        } else {
            Objects.requireNonNull(expectedNextAnchor, "expectedNextAnchor is required for an " + state + " cycle");
            if (!end.equals(expectedNextAnchor.minusDays(1))) {
                throw new IllegalArgumentException(
                        "end " + end + " must be the day before expectedNextAnchor " + expectedNextAnchor);
            }
        }
    }

    public static PayCycle closed(UUID userId, Integer salaryWalletId, LocalDate start, LocalDate end) {
        return new PayCycle(userId, salaryWalletId, start, end, null, CycleState.CLOSED);
    }

    public static PayCycle open(UUID userId, Integer salaryWalletId, LocalDate start, LocalDate expectedNextAnchor) {
        return new PayCycle(userId, salaryWalletId, start, expectedNextAnchor.minusDays(1), expectedNextAnchor,
                CycleState.OPEN);
    }

    public static PayCycle awaitingSalary(UUID userId, Integer salaryWalletId, LocalDate start,
                                          LocalDate expectedNextAnchor) {
        return new PayCycle(userId, salaryWalletId, start, expectedNextAnchor.minusDays(1), expectedNextAnchor,
                CycleState.AWAITING_SALARY);
    }

    /** Inclusive length in days ({@code start == end} → 1). */
    public int lengthDays() {
        return (int) (end.toEpochDay() - start.toEpochDay() + 1);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /**
     * 1-based day index of {@code date} within the cycle ({@code start} → 1). Not clamped: a date after
     * {@code end} (e.g. today while {@link CycleState#AWAITING_SALARY}) yields an index greater than
     * {@link #lengthDays()}; a date before {@code start} yields zero or a negative value.
     */
    public int dayIndex(LocalDate date) {
        return (int) (date.toEpochDay() - start.toEpochDay() + 1);
    }
}
