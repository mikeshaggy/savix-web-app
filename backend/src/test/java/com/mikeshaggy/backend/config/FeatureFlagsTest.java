package com.mikeshaggy.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FeatureFlagsConfig.class);

    @Test
    void defaultsAreAllFalse() {
        runner.run(context -> {
            FeatureFlags flags = context.getBean(FeatureFlags.class);
            assertThat(flags.payCycleV2()).isFalse();
            assertThat(flags.forecastV2()).isFalse();
            assertThat(flags.forecastV2Shadow()).isFalse();
            assertThat(flags.dashboardV2()).isFalse();
            assertThat(flags.insightsV2()).isFalse();
        });
    }

    @Test
    void kebabCaseKeysBindToCamelCaseComponents() {
        runner.withPropertyValues(
                "app.features.pay-cycle-v2=true",
                "app.features.forecast-v2=true",
                "app.features.forecast-v2-shadow=true",
                "app.features.dashboard-v2=true",
                "app.features.insights-v2=true"
        ).run(context -> {
            FeatureFlags flags = context.getBean(FeatureFlags.class);
            assertThat(flags.payCycleV2()).isTrue();
            assertThat(flags.forecastV2()).isTrue();
            assertThat(flags.forecastV2Shadow()).isTrue();
            assertThat(flags.dashboardV2()).isTrue();
            assertThat(flags.insightsV2()).isTrue();
        });
    }

    @Test
    void envPlaceholderOverridesDefault() {
        runner.withPropertyValues(
                "FEATURE_PAY_CYCLE_V2=true",
                "app.features.pay-cycle-v2=${FEATURE_PAY_CYCLE_V2:false}"
        ).run(context -> {
            FeatureFlags flags = context.getBean(FeatureFlags.class);
            assertThat(flags.payCycleV2()).isTrue();
            assertThat(flags.forecastV2()).isFalse();
            assertThat(flags.forecastV2Shadow()).isFalse();
            assertThat(flags.dashboardV2()).isFalse();
            assertThat(flags.insightsV2()).isFalse();
        });
    }
}
