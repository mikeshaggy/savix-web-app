package com.mikeshaggy.backend.ledger.service;

import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import com.mikeshaggy.backend.ledger.dto.WalletEntryResponse;
import com.mikeshaggy.backend.ledger.repo.WalletEntryRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final WalletService walletService;

    public List<WalletEntryResponse> getEntriesForUser(UUID userId) {
        return walletEntryRepository.findAllByUserId(userId).stream()
                .map(WalletEntryResponse::from)
                .toList();
    }

    public WalletEntryResponse getEntryByIdForUser(Long id, UUID userId) {
        WalletEntry entry = getEntryOrThrowForUser(id, userId);
        return WalletEntryResponse.from(entry);
    }

    public List<WalletEntryResponse> getEntriesByWalletIdForUser(Integer walletId, UUID userId) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        return walletEntryRepository.findByWalletIdAndUserId(walletId, userId).stream()
                .map(WalletEntryResponse::from)
                .toList();
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

    private WalletEntry getEntryOrThrowForUser(Long id, UUID userId) {
        return walletEntryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet entry not found with id: " + id));
    }
}
