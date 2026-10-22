package com.mikeshaggy.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(FeatureFlags.class)
public class FeatureFlagsConfig {

    @Autowired
    private FeatureFlags featureFlags;

    /** One line at startup for the unsupported combination; the dashboard then serves the legacy response. */
    @PostConstruct
    void warnOnUnsupportedCombination() {
        if (featureFlags.dashboardV2WithoutForecastV2()) {
            log.warn("app.features.dashboard-v2 is on but app.features.forecast-v2 is off: "
                    + "dashboard v2 needs Forecast v2 and stays inactive (legacy dashboard served)");
        }
    }
}
