package com.mikeshaggy.backend.transaction.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One calendar day's summed amount, as returned by the grouped daily-total
 * queries (e.g. {@link TransactionRepository#findDailyPaceEligibleVariableTotals}).
 * Days without transactions are absent here; callers zero-fill.
 */
public interface DailyTotalProjection {

    LocalDate getDay();

    BigDecimal getAmount();
}
