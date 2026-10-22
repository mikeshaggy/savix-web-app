package com.mikeshaggy.backend.insight.domain;

/**
 * Insights v2 event types (decision I1): one detector per type. Persisted by name in {@code insights.type}
 * (varchar 40), so a constant is never renamed once rows exist. The detectors themselves arrive in Stage 7.3;
 * the legacy {@code analytics.insight.InsightType} rules are unrelated and removed in Stage 7.4.
 */
public enum InsightType {
    CATEGORY_ABOVE_TYPICAL,
    SAVINGS_FUNDING_DAILY,
    PRE_PAYDAY_NEGATIVE,
    ONE_OFF_SKEWING_FORECAST,
    FIXED_PAYMENT_NOT_COVERABLE,
    FIXED_PAYMENT_AMOUNT_DRIFT,
    RECURRING_MERCHANT_DETECTED,
    CYCLE_CLOSED,
    SALARY_NOT_RECEIVED,
    HIGH_IMPULSE_SPENDING
}
