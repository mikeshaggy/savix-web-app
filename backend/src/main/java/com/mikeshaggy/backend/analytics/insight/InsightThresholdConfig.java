package com.mikeshaggy.backend.analytics.insight;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InsightThresholdConfig {

    @Bean
    public InsightThresholds insightThresholds() {
        return InsightThresholds.defaults();
    }
}
