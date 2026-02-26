package com.mikeshaggy.backend.transaction.repo;

import com.mikeshaggy.backend.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
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

    @Query("SELECT t FROM Transaction t WHERE t.category.name = 'salary' AND t.wallet.user.id = :userId ORDER BY t.transactionDate LIMIT 1")
    Optional<LocalDate> getDateOfLatestSalaryForUser(UUID userId);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.category WHERE t.wallet.id = :walletId " +
           "AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate " +
           "ORDER BY t.transactionDate DESC")
    List<Transaction> findByWalletIdAndTransactionDateBetween(Integer walletId,
                                                              LocalDate startDate,
                                                              LocalDate endDate);

    Optional<Transaction> findTopByWalletIdAndCategoryNameIgnoreCaseOrderByTransactionDateDesc(
            Integer walletId, String categoryName);

    List<Transaction> findTop2ByWalletIdAndCategoryNameIgnoreCaseOrderByTransactionDateDesc(
            Integer walletId, String categoryName);

    List<Transaction> findTop3ByWalletIdAndCategoryNameIgnoreCaseOrderByTransactionDateDesc(
            Integer walletId, String categoryName);
}
