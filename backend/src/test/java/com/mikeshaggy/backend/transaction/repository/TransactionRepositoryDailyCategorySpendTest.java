package com.mikeshaggy.backend.transaction.repository;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TransactionRepositoryDailyCategorySpendTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user;
    private Wallet wallet;
    private Category groceries;
    private Category dining;
    private Category hidden;
    private Category salary;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.builder()
                .email("cycle-test@example.com")
                .username("cycle-test")
                .passwordHash("hash")
                .build());
        wallet = entityManager.persist(Wallet.builder()
                .user(user)
                .name("Main")
                .balance(BigDecimal.ZERO)
                .build());
        groceries = category("Groceries", CategoryType.EXPENSE, "G", false);
        dining = category("Dining", CategoryType.EXPENSE, "D", false);
        hidden = category("Hidden", CategoryType.EXPENSE, "H", true);
        salary = category("Salary", CategoryType.INCOME, "S", false);
    }

    @Test
    void categoryIdsFilterReturnsOnlySelectedCategory() {
        tx(groceries, "10.00", LocalDate.of(2026, 5, 1), Importance.ESSENTIAL);
        tx(dining, "20.00", LocalDate.of(2026, 5, 1), Importance.NICE_TO_HAVE);
        flushAndClear();

        List<DailyCategorySpendProjection> rows = query(
                List.of(groceries.getId()), false, false, List.of(Importance.ESSENTIAL), true);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getCategoryId()).isEqualTo(groceries.getId());
        assertThat(rows.getFirst().getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void includedOnlyExcludesCategoriesHiddenFromTopCategories() {
        tx(groceries, "10.00", LocalDate.of(2026, 5, 1), Importance.ESSENTIAL);
        tx(hidden, "50.00", LocalDate.of(2026, 5, 1), Importance.ESSENTIAL);
        flushAndClear();

        List<DailyCategorySpendProjection> includedOnly = query(List.of(-1), true, true, List.of(Importance.ESSENTIAL), true);
        assertThat(includedOnly)
                .extracting(DailyCategorySpendProjection::getCategoryId)
                .containsExactly(groceries.getId());

        List<DailyCategorySpendProjection> all = query(List.of(-1), true, false, List.of(Importance.ESSENTIAL), true);
        assertThat(all)
                .extracting(DailyCategorySpendProjection::getCategoryId)
                .containsExactlyInAnyOrder(groceries.getId(), hidden.getId());
    }

    @Test
    void importanceFilterReturnsOnlyMatchingImportance() {
        tx(groceries, "10.00", LocalDate.of(2026, 5, 1), Importance.ESSENTIAL);
        tx(groceries, "30.00", LocalDate.of(2026, 5, 1), Importance.SHOULDNT_HAVE);
        flushAndClear();

        List<DailyCategorySpendProjection> rows = query(
                List.of(-1), true, false, List.of(Importance.SHOULDNT_HAVE), false);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getCategoryId()).isEqualTo(groceries.getId());
        assertThat(rows.getFirst().getAmount()).isEqualByComparingTo("30.00");
        assertThat(rows.getFirst().getTransactionCount()).isEqualTo(1L);
    }

    @Test
    void expenseTypeExcludesIncomeTransactions() {
        tx(groceries, "10.00", LocalDate.of(2026, 5, 1), Importance.ESSENTIAL);
        tx(salary, "5000.00", LocalDate.of(2026, 5, 1), null);
        flushAndClear();

        List<DailyCategorySpendProjection> rows = query(List.of(-1), true, false, List.of(Importance.ESSENTIAL), true);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getCategoryId()).isEqualTo(groceries.getId());
        assertThat(rows.getFirst().getAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void dateRangeIncludesStartAndEndButExcludesAfterRange() {
        tx(groceries, "10.00", LocalDate.of(2026, 5, 1), Importance.ESSENTIAL);
        tx(groceries, "20.00", LocalDate.of(2026, 5, 17), Importance.ESSENTIAL);
        tx(groceries, "999.00", LocalDate.of(2026, 5, 18), Importance.ESSENTIAL);
        flushAndClear();

        List<DailyCategorySpendProjection> rows = query(List.of(-1), true, false, List.of(Importance.ESSENTIAL), true);

        assertThat(rows).hasSize(2);
        assertThat(rows)
                .extracting(DailyCategorySpendProjection::getDate)
                .containsExactly(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 17));
        BigDecimal total = rows.stream()
                .map(DailyCategorySpendProjection::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("30.00");
    }

    private List<DailyCategorySpendProjection> query(
            List<Integer> categoryIds,
            boolean categoryIdsEmpty,
            boolean includedOnly,
            List<Importance> importance,
            boolean importanceEmpty) {
        return transactionRepository.findDailyCategorySpendByWalletUserDateRangeAndType(
                wallet.getId(),
                user.getId(),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 17),
                CategoryType.EXPENSE,
                categoryIds,
                categoryIdsEmpty,
                includedOnly,
                importance,
                importanceEmpty);
    }

    private Category category(String name, CategoryType type, String emoji, boolean excluded) {
        return entityManager.persist(Category.builder()
                .user(user)
                .name(name)
                .type(type)
                .emoji(emoji)
                .excludedFromTopCategories(excluded)
                .build());
    }

    private void tx(Category category, String amount, LocalDate date, Importance importance) {
        entityManager.persist(Transaction.builder()
                .wallet(wallet)
                .category(category)
                .title(UUID.randomUUID().toString().substring(0, 12))
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .importance(importance)
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
