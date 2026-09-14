package com.mikeshaggy.backend.common.paycycle;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Pay-cycle tuning (app.paycycle.*). {@code minCycleDays} is the anchor-merging window (decision T8):
 * an anchor-category transaction closer than this to the previous kept anchor joins its cycle.
 */
@ConfigurationProperties(prefix = "app.paycycle")
public record PayCycleProperties(@DefaultValue("20") int minCycleDays) {

    /**
     * Bounded above by {@link ExpectedPaydayResolver#MIN_CYCLE_DAYS}: the resolver may predict a payday as early as
     * {@code lastAnchor + MIN_CYCLE_DAYS + 1}, and a wider merge window would swallow that genuine salary.
     */
    public PayCycleProperties {
        if (minCycleDays < 1 || minCycleDays > ExpectedPaydayResolver.MIN_CYCLE_DAYS) {
            throw new IllegalArgumentException("app.paycycle.min-cycle-days must be between 1 and "
                    + ExpectedPaydayResolver.MIN_CYCLE_DAYS + ", was " + minCycleDays);
        }
    }
}
