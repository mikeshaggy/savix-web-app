package com.mikeshaggy.backend.analytics.forecast;

/** How much closed-cycle history backs a Forecast v2 result (Stage 4.5): {@code cyclesUsed ≥ 3 / ≥ 1 / 0}. */
public enum ForecastConfidence {
    HIGH,
    MEDIUM,
    LOW
}
