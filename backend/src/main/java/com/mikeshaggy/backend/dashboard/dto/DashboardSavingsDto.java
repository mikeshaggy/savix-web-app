package com.mikeshaggy.backend.dashboard.dto;

import java.math.BigDecimal;

/**
 * Stage 6.3 ({@code dashboard-v2}) — the user's savings wallet as seen from the salary wallet's current pay cycle.
 * Present only when exactly one non-fund wallet besides the salary wallet exists (that wallet is "savings").
 *
 * <p>{@code balance} is that wallet's actual balance. {@code netFlowThisCycle} is the Stage 6.2 transfer summary's
 * current-cycle {@code net} for that counter-wallet, from the salary wallet's side ({@code in − out}): positive =
 * savings funded the salary wallet this cycle, negative = the salary wallet moved money into savings; {@code 0.00}
 * without transfers this cycle. Both are as of today (the 6.2 convention), also when the dashboard is read for an
 * earlier {@code asOfDate}.
 */
public record DashboardSavingsDto(
        String counterWalletName,
        BigDecimal balance,
        BigDecimal netFlowThisCycle
) {
}
