package com.mikeshaggy.backend.transaction.repository;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TransactionRepositoryDateCountsTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user;
    private Wallet wallet;
    private Wallet otherWallet;
    private Category groceries;

    private static final LocalDate MAY_1 = LocalDate.of(2026, 5, 1);
    private static final LocalDate MAY_8 = LocalDate.of(2026, 5, 8);
    private static final LocalDate MAY_15 = LocalDate.of(2026, 5, 15);

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.builder()
                .email("datecounts@example.com")
                .username("datecounts")
                .passwordHash("hash")
                .build());
        wallet = entityManager.persist(Wallet.builder()
                .user(user)
                .name("Main")
                .balance(BigDecimal.ZERO)
                .build());
        otherWallet = entityManager.persist(Wallet.builder()
                .user(user)
                .name("Secondary")
                .balance(BigDecimal.ZERO)
                .build());
        groceries = entityManager.persist(Category.builder()
                .user(user)
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .emoji("G")
                .excludedFromTopCategories(false)
                .build());
    }

    @Test
    void countsWithStartAndEndDate_includeBoundariesAndExcludeOutside() {
        tx(wallet, MAY_1, Importance.ESSENTIAL);
        tx(wallet, MAY_8, Importance.ESSENTIAL);
        tx(wallet, MAY_8, Importance.ESSENTIAL);
        tx(wallet, MAY_15, Importance.ESSENTIAL);
        flushAndClear();

        List<TransactionDateCountProjection> counts = transactionRepository.findTransactionDateCounts(
                baseSpec(null, null, MAY_1, MAY_8, null));

        assertThat(counts)
                .extracting(TransactionDateCountProjection::getDate)
                .containsExactlyInAnyOrder(MAY_1, MAY_8);
        assertThat(countFor(counts, MAY_8)).isEqualTo(2L);
        assertThat(countFor(counts, MAY_15)).isNull();
    }

    @Test
    void countsWithOnlyStartDate_includeEverythingFromStartOnward() {
        tx(wallet, MAY_1, Importance.ESSENTIAL);
        tx(wallet, MAY_8, Importance.ESSENTIAL);
        tx(wallet, MAY_15, Importance.ESSENTIAL);
        flushAndClear();

        List<TransactionDateCountProjection> counts = transactionRepository.findTransactionDateCounts(
                baseSpec(null, null, MAY_8, null, null));

        assertThat(counts)
                .extracting(TransactionDateCountProjection::getDate)
                .containsExactlyInAnyOrder(MAY_8, MAY_15);
    }

    @Test
    void countsWithOnlyEndDate_includeEverythingUpToEnd() {
        tx(wallet, MAY_1, Importance.ESSENTIAL);
        tx(wallet, MAY_8, Importance.ESSENTIAL);
        tx(wallet, MAY_15, Importance.ESSENTIAL);
        flushAndClear();

        List<TransactionDateCountProjection> counts = transactionRepository.findTransactionDateCounts(
                baseSpec(null, null, null, MAY_8, null));

        assertThat(counts)
                .extracting(TransactionDateCountProjection::getDate)
                .containsExactlyInAnyOrder(MAY_1, MAY_8);
    }

    @Test
    void countsWithNoDates_includeEverythingForUser() {
        tx(wallet, MAY_1, Importance.ESSENTIAL);
        tx(wallet, MAY_8, Importance.ESSENTIAL);
        tx(wallet, MAY_15, Importance.ESSENTIAL);
        flushAndClear();

        List<TransactionDateCountProjection> counts = transactionRepository.findTransactionDateCounts(
                baseSpec(null, null, null, null, null));

        assertThat(counts)
                .extracting(TransactionDateCountProjection::getDate)
                .containsExactlyInAnyOrder(MAY_1, MAY_8, MAY_15);
    }

    @Test
    void countsWithImportanceFilter_returnOnlyMatchingImportance() {
        tx(wallet, MAY_8, Importance.ESSENTIAL);
        tx(wallet, MAY_8, Importance.SHOULDNT_HAVE);
        tx(wallet, MAY_15, Importance.SHOULDNT_HAVE);
        flushAndClear();

        List<TransactionDateCountProjection> counts = transactionRepository.findTransactionDateCounts(
                baseSpec(null, List.of(Importance.SHOULDNT_HAVE), null, null, null));

        assertThat(counts)
                .extracting(TransactionDateCountProjection::getDate)
                .containsExactlyInAnyOrder(MAY_8, MAY_15);
        assertThat(countFor(counts, MAY_8)).isEqualTo(1L);
        assertThat(countFor(counts, MAY_15)).isEqualTo(1L);
    }

    @Test
    void countsWithWalletImportanceAndDateRange_applyAllFilters() {
        tx(wallet, MAY_8, Importance.SHOULDNT_HAVE);
        tx(wallet, MAY_8, Importance.ESSENTIAL);        // wrong importance
        tx(wallet, MAY_1, Importance.SHOULDNT_HAVE);    // before range
        tx(otherWallet, MAY_8, Importance.SHOULDNT_HAVE); // wrong wallet
        flushAndClear();

        List<TransactionDateCountProjection> counts = transactionRepository.findTransactionDateCounts(
                baseSpec(wallet.getId(), List.of(Importance.SHOULDNT_HAVE), MAY_8, MAY_15, null));

        assertThat(counts).hasSize(1);
        assertThat(counts.getFirst().getDate()).isEqualTo(MAY_8);
        assertThat(counts.getFirst().getTransactionCount()).isEqualTo(1L);
    }

    /**
     * Reproduces the exact filter combination from the failing request
     * {@code /api/transactions?walletId=1&importances=...&startDate=2026-05-08&endDate=2026-06-03}
     * for every importance value. The query must build and execute without error.
     */
    @ParameterizedTest
    @EnumSource(Importance.class)
    void drillDownFilterDoesNotFailForAnyImportance(Importance importance) {
        tx(wallet, MAY_8, importance);
        flushAndClear();

        List<TransactionDateCountProjection> counts = transactionRepository.findTransactionDateCounts(
                baseSpec(wallet.getId(), List.of(importance), MAY_8, LocalDate.of(2026, 6, 3), null));

        assertThat(counts).hasSize(1);
        assertThat(counts.getFirst().getDate()).isEqualTo(MAY_8);
    }

    @Test
    void dateCountsMatchPaginatedQueryForSameFilterSet() {
        tx(wallet, MAY_1, Importance.ESSENTIAL);
        tx(wallet, MAY_8, Importance.SHOULDNT_HAVE);
        tx(wallet, MAY_8, Importance.SHOULDNT_HAVE);
        tx(wallet, MAY_15, Importance.SHOULDNT_HAVE);
        tx(otherWallet, MAY_8, Importance.SHOULDNT_HAVE);
        flushAndClear();

        Specification<Transaction> spec =
                baseSpec(wallet.getId(), List.of(Importance.SHOULDNT_HAVE), MAY_8, MAY_15, null);

        List<TransactionDateCountProjection> counts = transactionRepository.findTransactionDateCounts(spec);
        List<Transaction> rows = transactionRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "transactionDate"));

        long totalFromCounts = counts.stream()
                .mapToLong(TransactionDateCountProjection::getTransactionCount)
                .sum();

        assertThat(totalFromCounts).isEqualTo(rows.size());
        assertThat(counts)
                .extracting(TransactionDateCountProjection::getDate)
                .containsExactlyInAnyOrderElementsOf(
                        rows.stream().map(Transaction::getTransactionDate).distinct().toList());
    }

    private Specification<Transaction> baseSpec(
            Integer walletId,
            List<Importance> importances,
            LocalDate startDate,
            LocalDate endDate,
            String q) {
        return TransactionSpecifications.buildSpecification(
                user.getId(), walletId, null, null, importances, startDate, endDate, q);
    }

    private Long countFor(List<TransactionDateCountProjection> counts, LocalDate date) {
        return counts.stream()
                .filter(c -> c.getDate().equals(date))
                .map(TransactionDateCountProjection::getTransactionCount)
                .findFirst()
                .orElse(null);
    }

    private void tx(Wallet w, LocalDate date, Importance importance) {
        entityManager.persist(Transaction.builder()
                .wallet(w)
                .category(groceries)
                .title(UUID.randomUUID().toString().substring(0, 12))
                .amount(new BigDecimal("10.00"))
                .transactionDate(date)
                .importance(importance)
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
