package com.mikeshaggy.backend.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Stage 6.1 balance sparkline: the wallet's daily closing {@code balance_after} over {@code [from, to]}
 * (oldest first, carried forward over days without entries, no points before the first ledger entry), the actual
 * payday (cycle start) dates inside that window and the lowest daily closing balance of each of the last closed
 * pay cycles (oldest first). {@code paydays} and {@code cycleMinima} are empty for a wallet that is not the
 * salary wallet: pay cycles belong to the salary wallet only.
 * <p>
 * Balances are the ledger as of each day, never beyond {@code to} (today): when future-dated entries exist,
 * the last point differs from the stored wallet balance, which already includes them.
 */
public record BalanceSparklineResponse(
        Integer walletId,
        LocalDate from,
        LocalDate to,
        List<Point> points,
        List<LocalDate> paydays,
        List<CycleMinimum> cycleMinima
) {

    public record Point(LocalDate date, BigDecimal balance) {
    }

    /** Lowest daily closing balance of the closed cycle {@code [cycleStart, cycleEnd]}, first reached on {@code date}. */
    public record CycleMinimum(LocalDate cycleStart, LocalDate cycleEnd, BigDecimal minBalance, LocalDate date) {
    }
}
