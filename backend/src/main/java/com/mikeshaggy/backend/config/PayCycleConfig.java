package com.mikeshaggy.backend.config;

import com.mikeshaggy.backend.common.paycycle.PayCycleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PayCycleProperties.class)
public class PayCycleConfig {
}
