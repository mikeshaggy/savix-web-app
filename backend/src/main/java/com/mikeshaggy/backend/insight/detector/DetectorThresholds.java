package com.mikeshaggy.backend.insight.detector;

import java.math.BigDecimal;

/**
 * Every Insights v2 trigger threshold (Stage 7.3) in one place. Money in PLN; shares are compared exactly in
 * {@code BigDecimal} (never as doubles). Decision I2: an absolute floor before any percentage, and no percentage
 * on a base below {@link #MIN_PERCENT_BASE}.
 * <p>
 * These are <strong>initial product defaults</strong> chosen so that insights stay rare for a typical single-salary
 * household budgeted in PLN — tuning knobs for this product, not universal financial recommendations. Values marked
 * <em>plan</em> come from the Stage 7.3 table; values marked <em>product default</em> were set in Session X and
 * confirmed by the owner in Session X.1 (revisit after the first cycles of persisted output, Stage 9.4). Each one is
 * pinned by a boundary test in its detector's test class.
 */
public final class DetectorThresholds {

    private DetectorThresholds() {
    }

    /** No percentage is ever shown (or stored for display) against a base below this. */
    public static final BigDecimal MIN_PERCENT_BASE = new BigDecimal("100.00");

    // CategoryAboveTypical: current > median + max(100, 50 % of median), median ≥ 100, ≥ 3 cycles reached day d
    /** plan */
    public static final BigDecimal CATEGORY_MIN_BASELINE = new BigDecimal("100.00");
    /** plan */
    public static final BigDecimal CATEGORY_MIN_EXCESS = new BigDecimal("100.00");
    /** plan */
    public static final BigDecimal CATEGORY_EXCESS_SHARE = new BigDecimal("0.50");
    /**
     * product default. Decision I2 — a median of one or two cycles is a "previous period", not a typical; Stage 4
     * full history = 3.
     */
    public static final int CATEGORY_MIN_CYCLES = 3;

    // SavingsFundingDaily: net savings → salary over the last 2 closed cycles > 0, savings < median cycle variable
    public static final int SAVINGS_FLOW_CYCLES = 2;
    public static final int SAVINGS_MEDIAN_CYCLES = 6;

    // OneOffSkewingForecast: impact on the typical end balance ≥ 200
    /** plan */
    public static final BigDecimal ONE_OFF_MIN_IMPACT = new BigDecimal("200.00");

    // FixedPaymentAmountDrift: |paid − configured| > 5 % of configured and ≥ 10 PLN, two consecutive occurrences
    /** plan: strictly more than this share of the configured amount */
    public static final BigDecimal DRIFT_SHARE_PERCENT = new BigDecimal("5");
    /** product default: and at least this absolute difference (inclusive) */
    public static final BigDecimal DRIFT_MIN_DIFFERENCE = new BigDecimal("10.00");
    public static final int DRIFT_OCCURRENCES = 2;

    // RecurringMerchantDetected: ≥ 3 unlinked expenses, same normalised title, ±10 %, 25–35 days apart
    public static final int RECURRING_MIN_OCCURRENCES = 3;
    public static final int RECURRING_MIN_GAP_DAYS = 25;
    public static final int RECURRING_MAX_GAP_DAYS = 35;
    public static final BigDecimal RECURRING_AMOUNT_TOLERANCE = new BigDecimal("0.10");
    /** product default: the latest charge is at least this (inclusive) */
    public static final BigDecimal RECURRING_MIN_AMOUNT = new BigDecimal("10.00");
    /**
     * product default (inclusive). The latest charge must be at most one maximal gap old — a pattern that stopped is
     * not suggested.
     */
    public static final int RECURRING_MAX_AGE_DAYS = RECURRING_MAX_GAP_DAYS;

    // SalaryNotReceived: AWAITING_SALARY and today ≥ expected anchor + 5
    public static final int SALARY_GRACE_DAYS = 5;

    // HighImpulseSpending: SHOULDNT_HAVE > 300 and > 20 % of eligible variable
    /** plan (strict) */
    public static final BigDecimal IMPULSE_MIN_AMOUNT = new BigDecimal("300.00");
    public static final BigDecimal IMPULSE_SHARE_PERCENT = new BigDecimal("20");
}
