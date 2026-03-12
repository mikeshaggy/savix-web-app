package com.mikeshaggy.backend.ledger.repo;

import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.domain.WalletEntry;
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
            "ORDER BY e.entryDate DESC, e.createdAt DESC")
    List<WalletEntry> findByWalletIdAndUserId(Integer walletId, UUID userId);
}
