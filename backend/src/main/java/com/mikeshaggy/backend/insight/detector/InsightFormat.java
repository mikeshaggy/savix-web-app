package com.mikeshaggy.backend.insight.detector;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;

/** Text and payload helpers shared by the Stage 7.3 detectors (English copy; the UI localises by type + payload). */
final class InsightFormat {

    private InsightFormat() {
    }

    static String pln(BigDecimal amount) {
        return money(amount).toPlainString() + " PLN";
    }

    /**
     * {@code part / base × 100}, 2 dp — or {@code null} when {@code base} is below
     * {@link DetectorThresholds#MIN_PERCENT_BASE} (decision I2: no percentage on a small base).
     */
    static BigDecimal percentOf(BigDecimal part, BigDecimal base) {
        if (base == null || base.compareTo(DetectorThresholds.MIN_PERCENT_BASE) < 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    /** Whole-percent text of a {@link #percentOf} value. */
    static String wholePercent(BigDecimal percent) {
        return percent.setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** Cuts free text (a transaction or payment title) so a generated title stays within its column. */
    static String clip(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String trimmed = text.strip();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength - 1) + "…";
    }
}
