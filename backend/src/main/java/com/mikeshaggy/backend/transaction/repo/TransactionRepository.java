package com.mikeshaggy.backend.transaction.repo;

import com.mikeshaggy.backend.transaction.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    
    @Query("SELECT t FROM Transaction t JOIN FETCH t.wallet JOIN FETCH t.category WHERE t.id = :id AND t.wallet.user.id = :userId")
    Optional<Transaction> findByIdAndWalletUserId(Long id, UUID userId);
    
    @Query("SELECT t FROM Transaction t JOIN FETCH t.wallet JOIN FETCH t.category WHERE t.wallet.id = :walletId AND t.wallet.user.id = :userId ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<Transaction> findByWalletIdAndWalletUserId(Integer walletId, UUID userId);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.category WHERE t.wallet.id = :walletId " +
           "AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate " +
           "ORDER BY t.transactionDate DESC")
    List<Transaction> findByWalletIdAndTransactionDateBetween(Integer walletId,
                                                              LocalDate startDate,
                                                              LocalDate endDate);

    List<Transaction> findByWalletIdAndCategoryIdOrderByTransactionDateDesc(
            Integer walletId, Integer categoryId, Pageable pageable);


    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND c.type = 'INCOME'
        AND t.transactionDate BETWEEN :from AND :to
    """)
    BigDecimal sumIncomeByWalletIdAndDateRange(
        @Param("walletId") Integer walletId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Override
    @EntityGraph(attributePaths = {"wallet", "category"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);
}
