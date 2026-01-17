package com.mikeshaggy.backend.transaction.repo;

import com.mikeshaggy.backend.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT t FROM Transaction t WHERE t.wallet.user.id = :userId ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<Transaction> findAllByWalletUserId(UUID userId);
    
    @Query("SELECT t FROM Transaction t WHERE t.id = :id AND t.wallet.user.id = :userId")
    Optional<Transaction> findByIdAndWalletUserId(Long id, UUID userId);
    
    @Query("SELECT t FROM Transaction t WHERE t.wallet.id = :walletId AND t.wallet.user.id = :userId ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<Transaction> findByWalletIdAndWalletUserId(Integer walletId, UUID userId);
}
