package com.mikeshaggy.backend.user.dto;

import com.mikeshaggy.backend.config.FeatureFlags;

public record MeFeaturesResponse(
        boolean payCycleV2,
        boolean forecastV2,
        boolean dashboardV2,
        boolean insightsV2
) {
    public static MeFeaturesResponse from(FeatureFlags flags) {
        return new MeFeaturesResponse(
                flags.payCycleV2(),
                flags.forecastV2(),
                flags.dashboardV2(),
                flags.insightsV2()
        );
    }
}
