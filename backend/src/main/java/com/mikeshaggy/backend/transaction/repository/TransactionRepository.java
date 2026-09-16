package com.mikeshaggy.backend.transaction.repository;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction>, TransactionRepositoryCustom {
    
    @Query("SELECT t FROM Transaction t JOIN FETCH t.wallet JOIN FETCH t.category LEFT JOIN FETCH t.fixedPaymentOccurrence WHERE t.id = :id AND t.wallet.user.id = :userId")
    Optional<Transaction> findByIdAndWalletUserId(Long id, UUID userId);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.wallet JOIN FETCH t.category LEFT JOIN FETCH t.fixedPaymentOccurrence WHERE t.wallet.id = :walletId AND t.wallet.user.id = :userId ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<Transaction> findByWalletIdAndWalletUserId(Integer walletId, UUID userId);

    @Query("SELECT t FROM Transaction t JOIN FETCH t.category WHERE t.wallet.id = :walletId " +
           "AND t.wallet.user.id = :userId " +
           "AND t.transactionDate >= :startDate AND t.transactionDate <= :endDate " +
           "ORDER BY t.transactionDate DESC")
    List<Transaction> findByWalletIdAndWalletUserIdAndTransactionDateBetween(Integer walletId,
                                                                             UUID userId,
                                                                             LocalDate startDate,
                                                                             LocalDate endDate);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.category.id = :categoryId
        ORDER BY t.transactionDate DESC
    """)
    List<Transaction> findByWalletUserAndCategoryOrderByTransactionDateDesc(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("categoryId") Integer categoryId,
            Pageable pageable);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.category.id = :categoryId
        AND t.transactionDate <= :asOfDate
        ORDER BY t.transactionDate DESC
    """)
    List<Transaction> findByWalletUserCategoryAndDateLessThanEqualOrderByTransactionDateDesc(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("categoryId") Integer categoryId,
            @Param("asOfDate") LocalDate asOfDate,
            Pageable pageable);

    /**
     * Distinct dates of anchor-category transactions in one wallet, newest first, on or before {@code upTo}.
     * Feeds {@code PayCycleService.anchorDates}; same-day duplicates collapse here, the minimum-cycle-length
     * merge happens in the service.
     */
    @Query("""
        SELECT DISTINCT t.transactionDate FROM Transaction t
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.category.id = :categoryId
        AND t.transactionDate <= :upTo
        ORDER BY t.transactionDate DESC
    """)
    List<LocalDate> findAnchorDates(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("categoryId") Integer categoryId,
            @Param("upTo") LocalDate upTo,
            Pageable pageable);

    /**
     * Id of the regular (non-fund) wallet holding the user's most recent anchor-category transaction —
     * the same rule as the {@code users.salary_wallet_id} backfill in {@code 04_pay_cycle.sql}. Call with a
     * page of one.
     */
    @Query("""
        SELECT t.wallet.id FROM Transaction t
        WHERE t.wallet.user.id = :userId
        AND t.category.id = :categoryId
        AND t.wallet.isFund = false
        ORDER BY t.transactionDate DESC, t.id DESC
    """)
    List<Integer> findLatestAnchorWalletIds(
            @Param("userId") UUID userId,
            @Param("categoryId") Integer categoryId,
            Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
        AND c.type = :type
    """)
    BigDecimal sumByWalletUserDateRangeAndType(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("type") CategoryType type);

    /**
     * Sum of transactions of the given type that are NOT linked to a fixed
     * payment occurrence ("variable" spend). The NOT EXISTS subquery against the
     * occurrence FK keeps this a single aggregate query (no per-row navigation).
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
        AND c.type = :type
        AND NOT EXISTS (
            SELECT 1 FROM FixedPaymentOccurrence o WHERE o.transaction = t
        )
    """)
    BigDecimal sumUnlinkedByWalletUserDateRangeAndType(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("type") CategoryType type);

    /**
     * Variable (unlinked) EXPENSE spend that feeds the spending pace: rows flagged
     * {@code excludedFromPace} on the transaction or on its category are left out.
     * Reporting sums ({@link #sumByWalletUserDateRangeAndType},
     * {@link #sumUnlinkedByWalletUserDateRangeAndType}) are untouched by the flags.
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
        AND c.type = com.mikeshaggy.backend.category.domain.CategoryType.EXPENSE
        AND t.excludedFromPace = false
        AND c.excludedFromPace = false
        AND NOT EXISTS (
            SELECT 1 FROM FixedPaymentOccurrence o WHERE o.transaction = t
        )
    """)
    BigDecimal sumUnlinkedPaceEligibleByWalletUserDateRange(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Per-day totals of the same pace-eligible variable EXPENSE spend as
     * {@link #sumUnlinkedPaceEligibleByWalletUserDateRange}. Only days with at
     * least one eligible transaction are returned, ascending by day.
     */
    @Query("""
        SELECT t.transactionDate AS day,
               COALESCE(SUM(t.amount), 0) AS amount
        FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
        AND c.type = com.mikeshaggy.backend.category.domain.CategoryType.EXPENSE
        AND t.excludedFromPace = false
        AND c.excludedFromPace = false
        AND NOT EXISTS (
            SELECT 1 FROM FixedPaymentOccurrence o WHERE o.transaction = t
        )
        GROUP BY t.transactionDate
        ORDER BY t.transactionDate ASC
    """)
    List<DailyTotalProjection> findDailyPaceEligibleVariableTotals(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
        AND c.type = :type
        AND t.importance = :importance
    """)
    BigDecimal sumByWalletUserDateRangeTypeAndImportance(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("type") CategoryType type,
            @Param("importance") Importance importance);

    @Query("""
        SELECT c.id AS categoryId,
               c.name AS name,
               c.emoji AS emoji,
               COALESCE(SUM(t.amount), 0) AS amount,
               COUNT(t) AS transactionCount
        FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
        AND c.type = :type
        GROUP BY c.id, c.name, c.emoji
        ORDER BY SUM(t.amount) DESC, c.name ASC, c.id ASC
    """)
    List<CategoryBreakdownProjection> findCategorySpendByWalletUserAndDateRange(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("type") CategoryType type);

    @Query("""
        SELECT t.importance AS importance,
               COALESCE(SUM(t.amount), 0) AS amount,
               COUNT(t) AS count
        FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
        AND c.type = :type
        AND t.importance IS NOT NULL
        GROUP BY t.importance
    """)
    List<ImportanceBreakdownProjection> findImportanceBreakdownByWalletDateRangeAndType(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("type") CategoryType type);

    @Query("""
        SELECT t.transactionDate AS date,
               c.id AS categoryId,
               c.name AS categoryName,
               c.emoji AS emoji,
               COALESCE(SUM(t.amount), 0) AS amount,
               COUNT(t) AS transactions
        FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
        AND c.type = :type
        GROUP BY t.transactionDate, c.id, c.name, c.emoji
        ORDER BY t.transactionDate ASC, SUM(t.amount) DESC
    """)
    List<HeatmapProjection> findHeatmapByWalletDateRangeAndType(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("type") CategoryType type);

    @Query("""
        SELECT t.transactionDate AS date,
               c.id AS categoryId,
               c.name AS name,
               c.emoji AS emoji,
               COALESCE(SUM(t.amount), 0) AS amount,
               COUNT(t) AS transactionCount
        FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
        AND c.type = :type
        AND (:categoryIdsEmpty = true OR c.id IN :categoryIds)
        AND (:includedOnly = false OR c.excludedFromTopCategories = false)
        AND (:importanceEmpty = true OR t.importance IN :importance)
        GROUP BY t.transactionDate, c.id, c.name, c.emoji
        ORDER BY t.transactionDate ASC, c.name ASC, c.id ASC
    """)
    List<DailyCategorySpendProjection> findDailyCategorySpendByWalletUserDateRangeAndType(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("type") CategoryType type,
            @Param("categoryIds") List<Integer> categoryIds,
            @Param("categoryIdsEmpty") boolean categoryIdsEmpty,
            @Param("includedOnly") boolean includedOnly,
            @Param("importance") List<Importance> importance,
            @Param("importanceEmpty") boolean importanceEmpty);

    @Query("""
        SELECT c.id AS categoryId,
               c.name AS name,
               c.emoji AS emoji,
               COALESCE(SUM(t.amount), 0) AS amount,
               COUNT(t) AS transactionCount
        FROM Transaction t
        JOIN t.category c
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
        AND c.type = :type
        AND c.excludedFromTopCategories = false
        GROUP BY c.id, c.name, c.emoji
        ORDER BY SUM(t.amount) DESC, c.name ASC, c.id ASC
    """)
    List<CategoryBreakdownProjection> findIncludedCategorySpendByWalletUserAndDateRange(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("type") CategoryType type);

    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE t.wallet.id = :walletId
        AND t.wallet.user.id = :userId
        AND t.transactionDate BETWEEN :from AND :to
    """)
    long countByWalletUserDateRange(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Override
    @EntityGraph(attributePaths = {"wallet", "category", "fixedPaymentOccurrence"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"wallet", "category", "fixedPaymentOccurrence"})
    List<Transaction> findAll(Specification<Transaction> spec, Sort sort);
}
