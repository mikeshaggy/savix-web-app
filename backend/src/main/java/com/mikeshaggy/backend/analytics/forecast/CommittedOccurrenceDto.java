package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One unpaid fixed-payment occurrence the forecast counts as committed: a Stage 3 tile row bucketed
 * {@code OVERDUE}, {@code DUE_SOON} or {@code LATER_THIS_CYCLE}. {@code AFTER_PAYDAY} and paid rows never appear.
 */
public record CommittedOccurrenceDto(
        Long occurrenceId,
        String title,
        String categoryName,
        BigDecimal expectedAmount,
        LocalDate dueDate,
        FixedOccurrenceBucket bucket
) {
    static CommittedOccurrenceDto from(FixedOccurrenceRowDto row) {
        return new CommittedOccurrenceDto(
                row.occurrenceId(),
                row.title(),
                row.categoryName(),
                row.expectedAmount(),
                row.dueDate(),
                row.bucket());
    }
}
