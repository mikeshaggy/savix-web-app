package com.mikeshaggy.backend.analytics.overview;

import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OverviewStatusCalculator {

    public OverviewStatus compute(SpendingProjectionDto projection) {
        if (!projection.projectionAvailable()) {
            return OverviewStatus.NEUTRAL;
        }
        if (projection.projectedEndBalance().compareTo(BigDecimal.ZERO) < 0
                || projection.safeToSpendToday().compareTo(BigDecimal.ZERO) < 0) {
            return OverviewStatus.CRITICAL;
        }
        if (projection.incomeForPeriod().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ninetyPct = projection.incomeForPeriod().multiply(new BigDecimal("0.90"));
            if (projection.projectedPeriodExpenses().compareTo(ninetyPct) > 0) {
                return OverviewStatus.WARNING;
            }
        }
        return OverviewStatus.GOOD;
    }
}
