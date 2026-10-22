package com.mikeshaggy.backend.wallet.domain;

import java.util.List;
import java.util.Optional;

/**
 * Which wallet is "savings" relative to the salary wallet: the user's only other regular (non-fund) wallet. With
 * none or several there is no savings wallet — the product never guesses. Shared by the dashboard {@code savings}
 * section (Stage 6.3) and the Insights v2 savings detectors (Stage 7.3) so both always name the same wallet.
 */
public final class SavingsWallets {

    private SavingsWallets() {
    }

    /** @param nonFundWallets every non-fund wallet of the user ({@code WalletRepository#findByUserIdAndIsFundFalse}) */
    public static Optional<Wallet> single(List<Wallet> nonFundWallets, Integer salaryWalletId) {
        List<Wallet> candidates = nonFundWallets.stream()
                .filter(wallet -> !salaryWalletId.equals(wallet.getId()))
                .toList();
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }
}
