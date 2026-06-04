package com.mikeshaggy.backend.fund.dto;

import com.mikeshaggy.backend.fund.domain.FundMovementType;
import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.wallet.domain.Wallet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single fund movement, derived from a {@link Transfer} involving the fund's
 * hidden wallet.
 * <p>
 * The hidden fund wallet ID is intentionally NOT exposed. Only the
 * <em>counterparty</em> wallet (a normal, user-facing wallet) is surfaced:
 * <ul>
 *   <li>DEPOSIT — fund wallet is the transfer's {@code toWallet}; counterparty is {@code fromWallet}</li>
 *   <li>WITHDRAWAL — fund wallet is the transfer's {@code fromWallet}; counterparty is {@code toWallet}</li>
 * </ul>
 */
public record FundMovementResponse(
        Long id,
        Long transferId,
        FundMovementType type,
        BigDecimal amount,
        LocalDate date,
        Integer counterpartyWalletId,
        String counterpartyWalletName,
        String notes,
        Instant createdAt
) {
    /**
     * Maps a transfer to a movement relative to the given fund wallet.
     *
     * @param transfer     the transfer (with both wallets loaded)
     * @param fundWalletId the hidden fund wallet ID — used only to determine
     *                     direction and counterparty; never placed in the response
     */
    public static FundMovementResponse from(Transfer transfer, Integer fundWalletId) {
        boolean isDeposit = transfer.getToWallet().getId().equals(fundWalletId);
        Wallet counterparty = isDeposit ? transfer.getFromWallet() : transfer.getToWallet();

        return new FundMovementResponse(
                transfer.getId(),
                transfer.getId(),
                isDeposit ? FundMovementType.DEPOSIT : FundMovementType.WITHDRAWAL,
                transfer.getAmount(),
                transfer.getTransferDate(),
                counterparty.getId(),
                counterparty.getName(),
                transfer.getNotes(),
                transfer.getCreatedAt()
        );
    }
}
