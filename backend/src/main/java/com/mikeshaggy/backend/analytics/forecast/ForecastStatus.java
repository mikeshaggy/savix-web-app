package com.mikeshaggy.backend.analytics.forecast;

/**
 * Forecast v2 verdict for the open pay cycle (Stage 4.5). Computed by {@link ForecastV2Calculator}; whether a
 * verdict applies at all (PAY_CYCLE, salary wallet, OPEN state) is decided by the caller (Stage 4.7) and is
 * expressed there as a {@code null} status.
 */
public enum ForecastStatus {
    /** The expected variable spend fits into the discretionary balance and every commitment is coverable. */
    FINE,
    /** Nothing is uncoverable, but the expected variable spend exceeds the discretionary balance. */
    TIGHT,
    /**
     * The discretionary balance is already negative, or a committed occurrence due before the next salary
     * cannot be covered on the pessimistic variable-spend path when it becomes due.
     */
    SHORT
}
