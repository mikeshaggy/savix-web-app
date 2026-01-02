package com.mikeshaggy.backend.repository;

import com.mikeshaggy.backend.domain.transaction.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT t FROM Transaction t WHERE t.wallet.id = :walletId")
    List<Transaction> findByWalletId(Integer walletId);
}
