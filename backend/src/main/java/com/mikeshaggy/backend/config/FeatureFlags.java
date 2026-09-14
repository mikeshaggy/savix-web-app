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
}
