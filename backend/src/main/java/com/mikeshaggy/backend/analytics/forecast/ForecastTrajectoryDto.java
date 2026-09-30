package com.mikeshaggy.backend.analytics.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Stage 5.4 trajectory of the open pay cycle, built by {@link ForecastTrajectoryBuilder}: cumulative
 * <em>pace-eligible variable</em> spend by cycle day for the current cycle ({@code current}, day 1 … today) and the
 * same cumulative measure across the historical baseline cycles ({@code typical}, day 1 … the current cycle's last
 * day). Days are aligned by cycle-day index (day 1 = the salary day), not length-normalised, as the Comparison page
 * does. Read-only presentation data: no forecast figure is derived from it.
 */
public record ForecastTrajectoryDto(
        List<Point> current,
        List<BandPoint> typical
) {
    public ForecastTrajectoryDto {
        current = current == null ? List.of() : List.copyOf(current);
        typical = typical == null ? List.of() : List.copyOf(typical);
    }

    /** Cumulative pace-eligible variable spend of the current cycle through {@code date} (day {@code dayIndex}). */
    public record Point(int dayIndex, LocalDate date, BigDecimal cumulative) {
    }

    /**
     * Historical cumulative spend by day {@code dayIndex} across the {@code cycles} baseline cycles that lasted at
     * least that many days. With three or more: R-7 p25 / median / p75. With one or two: the median with the
     * ±25 % band of the Stage 4.3 rule. Days no baseline cycle reached are omitted, never padded.
     */
    public record BandPoint(int dayIndex, LocalDate date, BigDecimal p25, BigDecimal median, BigDecimal p75,
                            int cycles) {
    }
}
