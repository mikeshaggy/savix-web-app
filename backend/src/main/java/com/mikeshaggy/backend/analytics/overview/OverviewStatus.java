package com.mikeshaggy.backend.analytics.overview;

public enum OverviewStatus {
    /** Projection available and spending is well within budget. */
    GOOD,
    /** Projected expenses exceed 90% of income for the period. */
    WARNING,
    /** Projected end balance or safe-to-spend is negative. */
    CRITICAL,
    /** No projection available (historical period or missing data). */
    NEUTRAL
}
