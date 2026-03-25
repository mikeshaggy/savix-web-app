package com.mikeshaggy.backend.ledger.service;

import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletEntryResponse;
import com.mikeshaggy.backend.ledger.repo.WalletEntryRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repo.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletEntryService {

    private final WalletEntryRepository walletEntryRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceHistoryQueryService walletBalanceHistoryQueryService;

    public List<WalletEntryResponse> getEntriesByWalletIdForUser(
            Integer walletId,
            UUID userId,
            LocalDate from,
            LocalDate to,
            Integer limit
    ) {
        if (!walletRepository.existsByIdAndUserId(walletId, userId)) {
            throw new EntityNotFoundException("Wallet not found with id: " + walletId);
        }

        if (limit != null && limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }

        Pageable pageable = limit == null ? Pageable.unpaged() : Pageable.ofSize(limit);

        return walletEntryRepository.findByWalletIdAndUserIdForHistory(walletId, userId, from, to, pageable).stream()
                .map(WalletEntryResponse::from)
                .toList();
    }

    public WalletBalanceHistoryResponse getBalanceHistoryByWalletIdForUser(Integer walletId, UUID userId) {
        return walletBalanceHistoryQueryService.getBalanceHistoryByWalletIdForUser(walletId, userId);
    }

    public WalletBalanceHistoryResponse getBalanceHistoryByWalletIdForUser(
            Integer walletId,
            UUID userId,
            LocalDate from,
            LocalDate to,
            Integer page,
            Integer size
    ) {
        return walletBalanceHistoryQueryService.getBalanceHistoryByWalletIdForUser(
                walletId,
                userId,
                from,
                to,
                page,
                size
        );
    }

    @Transactional
    public WalletEntry createEntry(Wallet wallet, BigDecimal amountSigned, LocalDate entryDate,
                                   SourceType sourceType, Long sourceId) {
        WalletEntry entry = WalletEntry.builder()
                .wallet(wallet)
                .amountSigned(amountSigned)
                .entryDate(entryDate)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .build();

        WalletEntry savedEntry = walletEntryRepository.save(entry);

        log.info("Created wallet entry (id: {}) for wallet {}: {} on {}, source: {} #{}",
                savedEntry.getId(), wallet, amountSigned, entryDate, sourceType, sourceId);

        return savedEntry;
    }
}
