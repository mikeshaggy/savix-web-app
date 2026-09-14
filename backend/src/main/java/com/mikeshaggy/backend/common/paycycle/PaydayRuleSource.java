package com.mikeshaggy.backend.common.paycycle;

/**
 * Where a {@link PaydayRule} came from, in {@link ExpectedPaydayResolver} priority order.
 */
public enum PaydayRuleSource {
    /** Explicitly configured by the user. */
    CONFIGURED,
    /** Learned from the recent anchor history (median day-of-month + observed weekend shift). */
    LEARNED,
    /** Fallback: last anchor + median historical cycle length. */
    MEDIAN_LENGTH,
    /** Last resort: last anchor + 1 month. */
    PLUS_ONE_MONTH
}
