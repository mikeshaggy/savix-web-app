package com.mikeshaggy.backend.analytics.insight;

import java.math.BigDecimal;

public record InsightThresholds(
        BigDecimal categorySpikeMultiplier,
        BigDecimal minimumCategorySpikeAmount,
        BigDecimal impulseShareThresholdPercent,
        BigDecimal paceMultiplier,
        BigDecimal lowSavingsRateThresholdPercent,
        BigDecimal goodMonthExpenseMultiplier,
        BigDecimal goodMonthSavingsRatePercent
) {

    private static final BigDecimal CATEGORY_SPIKE_MULTIPLIER = new BigDecimal("1.30");
    private static final BigDecimal MINIMUM_CATEGORY_SPIKE_AMOUNT = new BigDecimal("50.00");
    private static final BigDecimal IMPULSE_SHARE_THRESHOLD_PERCENT = new BigDecimal("20");
    private static final BigDecimal PACE_MULTIPLIER = new BigDecimal("1.20");
    private static final BigDecimal LOW_SAVINGS_RATE_THRESHOLD_PERCENT = new BigDecimal("10");
    private static final BigDecimal GOOD_MONTH_EXPENSE_MULTIPLIER = new BigDecimal("0.90");
    private static final BigDecimal GOOD_MONTH_SAVINGS_RATE_PERCENT = new BigDecimal("30");

    public static InsightThresholds defaults() {
        return new InsightThresholds(
                CATEGORY_SPIKE_MULTIPLIER,
                MINIMUM_CATEGORY_SPIKE_AMOUNT,
                IMPULSE_SHARE_THRESHOLD_PERCENT,
                PACE_MULTIPLIER,
                LOW_SAVINGS_RATE_THRESHOLD_PERCENT,
                GOOD_MONTH_EXPENSE_MULTIPLIER,
                GOOD_MONTH_SAVINGS_RATE_PERCENT);
    }
}
