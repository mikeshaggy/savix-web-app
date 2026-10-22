package com.mikeshaggy.backend.insight.detector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Stage 7.3 initial product defaults (Session X.1 owner decision) next to the plan values, so a change to any
 * of them is a deliberate, reviewed edit. The boundary behaviour of each value is tested in its detector's test class.
 */
class DetectorThresholdsTest {

    @Test
    void initialProductDefaults() {
        assertThat(DetectorThresholds.DRIFT_SHARE_PERCENT).isEqualByComparingTo("5");
        assertThat(DetectorThresholds.DRIFT_MIN_DIFFERENCE).isEqualByComparingTo("10.00");
        assertThat(DetectorThresholds.RECURRING_MIN_AMOUNT).isEqualByComparingTo("10.00");
        assertThat(DetectorThresholds.RECURRING_MAX_AGE_DAYS).isEqualTo(35);
        assertThat(DetectorThresholds.CATEGORY_MIN_CYCLES).isEqualTo(3);
    }

    @Test
    void planValues() {
        assertThat(DetectorThresholds.CATEGORY_MIN_BASELINE).isEqualByComparingTo("100.00");
        assertThat(DetectorThresholds.CATEGORY_MIN_EXCESS).isEqualByComparingTo("100.00");
        assertThat(DetectorThresholds.CATEGORY_EXCESS_SHARE).isEqualByComparingTo("0.50");
        assertThat(DetectorThresholds.ONE_OFF_MIN_IMPACT).isEqualByComparingTo("200.00");
        assertThat(DetectorThresholds.RECURRING_MIN_OCCURRENCES).isEqualTo(3);
        assertThat(DetectorThresholds.RECURRING_MIN_GAP_DAYS).isEqualTo(25);
        assertThat(DetectorThresholds.RECURRING_MAX_GAP_DAYS).isEqualTo(35);
        assertThat(DetectorThresholds.RECURRING_AMOUNT_TOLERANCE).isEqualByComparingTo("0.10");
        assertThat(DetectorThresholds.SALARY_GRACE_DAYS).isEqualTo(5);
        assertThat(DetectorThresholds.IMPULSE_MIN_AMOUNT).isEqualByComparingTo("300.00");
        assertThat(DetectorThresholds.IMPULSE_SHARE_PERCENT).isEqualByComparingTo("20");
        assertThat(DetectorThresholds.MIN_PERCENT_BASE).isEqualByComparingTo("100.00");
    }
}
