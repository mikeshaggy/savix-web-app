package com.mikeshaggy.backend.transfer.repo;

import com.mikeshaggy.backend.transfer.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    @Query("SELECT t FROM Transfer t JOIN FETCH t.fromWallet JOIN FETCH t.toWallet WHERE t.fromWallet.user.id = :userId OR t.toWallet.user.id = :userId " +
            "ORDER BY t.transferDate DESC, t.createdAt DESC")
    List<Transfer> findAllByUserId(UUID userId);

    @Query("SELECT t FROM Transfer t JOIN FETCH t.fromWallet JOIN FETCH t.toWallet WHERE t.id = :id " +
            "AND (t.fromWallet.user.id = :userId OR t.toWallet.user.id = :userId)")
    Optional<Transfer> findByIdAndUserId(Long id, UUID userId);

    @Query("SELECT t FROM Transfer t JOIN FETCH t.fromWallet JOIN FETCH t.toWallet WHERE (t.fromWallet.id = :walletId OR t.toWallet.id = :walletId) " +
            "AND (t.fromWallet.user.id = :userId OR t.toWallet.user.id = :userId) " +
            "ORDER BY t.transferDate DESC, t.createdAt DESC")
    List<Transfer> findByWalletIdAndUserId(Integer walletId, UUID userId);
}
