package com.mikeshaggy.backend.common.period;

import com.mikeshaggy.backend.common.paycycle.CycleState;

import java.time.LocalDate;

/**
 * A resolved reporting/planning period.
 * <p>
 * {@code cycleState}, {@code expectedNextAnchorDate} and {@code salaryWallet} are pay-cycle metadata: they are
 * populated only by the {@code pay-cycle-v2} resolvers for {@code PAY_CYCLE} / {@code LAST_PAY_CYCLE} periods and
 * stay {@code null} for {@code MONTHLY} / {@code CUSTOM} periods and for every legacy resolver path.
 */
public record PeriodDto(
        LocalDate startDate,
        LocalDate endDate,
        LocalDate billingEndDate,
        PeriodType periodType,
        CycleState cycleState,
        LocalDate expectedNextAnchorDate,
        Boolean salaryWallet
) {

    /** A period without pay-cycle metadata (the legacy shape). */
    public static PeriodDto of(LocalDate startDate, LocalDate endDate, LocalDate billingEndDate, PeriodType periodType) {
        return new PeriodDto(startDate, endDate, billingEndDate, periodType, null, null, null);
    }

    /**
     * Inclusive end of the elapsed part of the period as of {@code today}: a {@code PAY_CYCLE} whose end lies in
     * the future is cut at {@code today}; every other period (and a pay cycle that already ended) keeps its end.
     * Readers summing "to date" amounts use this instead of {@link #endDate()}.
     */
    public LocalDate elapsedEndDate(LocalDate today) {
        return periodType == PeriodType.PAY_CYCLE && endDate.isAfter(today) ? today : endDate;
    }
}
