package com.mikeshaggy.backend.analytics.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One current-cycle expense the one-off layer flagged (Stage 4.6), as exposed by {@link ForecastV2Dto}.
 *
 * <p>{@code shareOfVariable} is in <b>percentage points</b> ({@code 19.33} = 19.33 %), against the gross
 * pre-exclusion variable spend of the cycle. {@code excluded} rows are already out of the spending pace and
 * carry {@code 0.00} for both impacts; they stay listed so the UI can show "excluded".
 */
public record OneOffDto(
        Long transactionId,
        LocalDate date,
        String title,
        String categoryName,
        BigDecimal amount,
        BigDecimal shareOfVariable,
        BigDecimal impactOnExpectedVariableRemaining,
        BigDecimal impactOnExpectedEndBalance,
        boolean excluded
) {
    static OneOffDto from(OneOffDetector.OneOff oneOff) {
        return new OneOffDto(
                oneOff.transactionId(),
                oneOff.date(),
                oneOff.title(),
                oneOff.categoryName(),
                oneOff.amount(),
                oneOff.shareOfVariable(),
                oneOff.impactOnExpectedVariableRemaining(),
                oneOff.impactOnExpectedEndBalance(),
                oneOff.excluded());
    }
}
