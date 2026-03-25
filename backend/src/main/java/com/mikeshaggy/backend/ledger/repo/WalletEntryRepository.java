package com.mikeshaggy.backend.ledger.repo;

import com.mikeshaggy.backend.ledger.domain.WalletEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, Long> {

    @Query("SELECT e FROM WalletEntry e JOIN FETCH e.wallet WHERE e.wallet.user.id = :userId " +
            "ORDER BY e.entryDate DESC, e.createdAt DESC")
    List<WalletEntry> findAllByUserId(UUID userId);

    @Query("SELECT e FROM WalletEntry e JOIN FETCH e.wallet WHERE e.id = :id AND e.wallet.user.id = :userId")
    Optional<WalletEntry> findByIdAndUserId(Long id, UUID userId);

    @Query("SELECT e FROM WalletEntry e JOIN FETCH e.wallet WHERE e.wallet.id = :walletId AND e.wallet.user.id = :userId " +
            "AND (cast(:from as LocalDate) IS NULL OR e.entryDate >= :from) " +
            "AND (cast(:to as LocalDate) IS NULL OR e.entryDate <= :to) " +
            "ORDER BY e.entryDate DESC, e.createdAt DESC, e.id DESC")
    List<WalletEntry> findByWalletIdAndUserIdForHistory(
            Integer walletId,
            UUID userId,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );

    @Query("SELECT e FROM WalletEntry e JOIN FETCH e.wallet WHERE e.wallet.id = :walletId " +
            "ORDER BY e.entryDate ASC, e.createdAt ASC, e.id ASC")
    List<WalletEntry> findByWalletIdOrderByLedgerOrder(Integer walletId);
}
