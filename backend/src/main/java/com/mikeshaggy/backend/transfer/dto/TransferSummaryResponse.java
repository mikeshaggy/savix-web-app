package com.mikeshaggy.backend.transfer.dto;

import com.mikeshaggy.backend.common.paycycle.CycleState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Stage 6.2 transfers summary of one wallet per pay cycle. Every transfer row counts once, from this wallet's
 * side: {@code in} when it is the target, {@code out} when it is the source; {@code net = in − out}. Fund
 * movements are not transfers here. {@code currentCycle} counts transfers from the cycle start through today;
 * {@code cycles} are the most recent closed cycles, newest first. Both are absent ({@code null} / empty) for a
 * wallet that is not the salary wallet. {@code counterWallets} lists every wallet this one ever exchanged
 * transfers with, and its current balance.
 */
public record TransferSummaryResponse(
        Integer walletId,
        CycleSummary currentCycle,
        List<CycleSummary> cycles,
        List<CounterWallet> counterWallets
) {

    public record CycleSummary(
            LocalDate start,
            LocalDate end,
            CycleState state,
            BigDecimal in,
            BigDecimal out,
            BigDecimal net,
            List<CounterWalletFlow> counterWallets
    ) {
    }

    public record CounterWalletFlow(
            Integer walletId,
            String walletName,
            BigDecimal in,
            BigDecimal out,
            BigDecimal net
    ) {
    }

    public record CounterWallet(Integer walletId, String walletName, BigDecimal balance) {
    }
}
