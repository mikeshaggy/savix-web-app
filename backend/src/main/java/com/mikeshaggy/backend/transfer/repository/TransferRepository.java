package com.mikeshaggy.backend.transfer.repository;

import com.mikeshaggy.backend.transfer.domain.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    @Query("SELECT t FROM Transfer t JOIN FETCH t.fromWallet JOIN FETCH t.toWallet " +
            "WHERE (t.fromWallet.user.id = :userId OR t.toWallet.user.id = :userId) " +
            "AND t.fromWallet.isFund = false AND t.toWallet.isFund = false " +
            "ORDER BY t.transferDate DESC, t.createdAt DESC")
    List<Transfer> findAllByUserId(UUID userId);

    @Query("SELECT t FROM Transfer t JOIN FETCH t.fromWallet JOIN FETCH t.toWallet WHERE t.id = :id " +
            "AND (t.fromWallet.user.id = :userId OR t.toWallet.user.id = :userId)")
    Optional<Transfer> findByIdAndUserId(Long id, UUID userId);
    
    @Query("SELECT t FROM Transfer t JOIN FETCH t.fromWallet JOIN FETCH t.toWallet " +
            "WHERE (t.fromWallet.id = :walletId OR t.toWallet.id = :walletId) " +
            "AND (t.fromWallet.user.id = :userId OR t.toWallet.user.id = :userId) " +
            "AND t.fromWallet.isFund = false AND t.toWallet.isFund = false " +
            "ORDER BY t.transferDate DESC, t.createdAt DESC")
    List<Transfer> findByWalletIdAndUserId(Integer walletId, UUID userId);

    /**
     * Paginated movements involving a single fund wallet (one side of the transfer
     * is the fund wallet). Used by the fund-domain movement history endpoint.
     * <p>
     * Both wallets are fetched (to-one joins, pagination-safe) so the counterparty
     * name is available without an N+1. Ordering comes from the {@link Pageable}'s
     * sort; an explicit {@code countQuery} avoids deriving a count from the fetch join.
     */
    @Query(value = "SELECT t FROM Transfer t " +
            "JOIN FETCH t.fromWallet fw JOIN FETCH t.toWallet tw " +
            "WHERE (fw.id = :fundWalletId OR tw.id = :fundWalletId) " +
            "AND (fw.user.id = :userId OR tw.user.id = :userId)",
            countQuery = "SELECT COUNT(t) FROM Transfer t " +
            "WHERE (t.fromWallet.id = :fundWalletId OR t.toWallet.id = :fundWalletId) " +
            "AND (t.fromWallet.user.id = :userId OR t.toWallet.user.id = :userId)")
    Page<Transfer> findFundMovements(@Param("userId") UUID userId,
                                     @Param("fundWalletId") Integer fundWalletId,
                                     Pageable pageable);
}
