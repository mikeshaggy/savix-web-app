package com.mikeshaggy.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Roadmap feature flags (app.features.*). All default to false; forecastV2Shadow is
 * backend-only and must never be exposed through the API.
 */
@ConfigurationProperties(prefix = "app.features")
public record FeatureFlags(
        @DefaultValue("false") boolean payCycleV2,
        @DefaultValue("false") boolean forecastV2,
        @DefaultValue("false") boolean forecastV2Shadow,
        @DefaultValue("false") boolean dashboardV2,
        @DefaultValue("false") boolean insightsV2
) {

    /**
     * Whether the dashboard v2 response/layout is served. The v2 dashboard presents Forecast v2 semantics, so
     * {@code dashboard-v2} without {@code forecast-v2} is an unsupported combination and counts as inactive
     * ({@code /api/me.features} still reports the raw values; the frontend applies the same conjunction).
     */
    public boolean dashboardV2Active() {
        return dashboardV2 && forecastV2;
    }

    /** {@code dashboard-v2} requested without the {@code forecast-v2} it depends on (warned about at startup). */
    public boolean dashboardV2WithoutForecastV2() {
        return dashboardV2 && !forecastV2;
    }
}
