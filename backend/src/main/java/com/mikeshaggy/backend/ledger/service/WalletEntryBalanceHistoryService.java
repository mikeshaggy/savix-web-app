package com.mikeshaggy.backend.ledger.service;

import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import com.mikeshaggy.backend.ledger.repo.WalletEntryRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repo.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletEntryBalanceHistoryService {

    private final WalletEntryRepository walletEntryRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public void recalculateWalletLedger(Long walletId) {
        Integer normalizedWalletId = normalizeWalletId(walletId);
        Wallet wallet = walletRepository.findById(normalizedWalletId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found with id: " + walletId));

        List<WalletEntry> entries = walletEntryRepository.findByWalletIdOrderByLedgerOrder(normalizedWalletId);
        BigDecimal runningBalance = BigDecimal.ZERO;

        for (WalletEntry entry : entries) {
            runningBalance = runningBalance.add(entry.getAmountSigned());
            entry.setBalanceAfter(runningBalance);
        }

        if (!entries.isEmpty()) {
            walletEntryRepository.saveAll(entries);
        }

        wallet.setBalance(runningBalance);
        walletRepository.save(wallet);

        log.info("Recalculated wallet ledger for wallet {}: {} entries, ending balance {}",
                normalizedWalletId, entries.size(), runningBalance);
    }

    private Integer normalizeWalletId(Long walletId) {
        if (walletId == null) {
            throw new IllegalArgumentException("walletId cannot be null");
        }
        if (walletId <= 0 || walletId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("walletId out of supported range: " + walletId);
        }
        return walletId.intValue();
    }
}
