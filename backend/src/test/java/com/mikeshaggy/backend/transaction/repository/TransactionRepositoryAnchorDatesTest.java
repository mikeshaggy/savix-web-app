package com.mikeshaggy.backend.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** The two pay-cycle queries behind {@code PayCycleService}: anchor dates per salary wallet and the wallet fallback. */
@DataJpaTest
class TransactionRepositoryAnchorDatesTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user;
    private User otherUser;
    private Wallet salaryWallet;
    private Wallet savingsWallet;
    private Wallet fundWallet;
    private Wallet otherUsersWallet;
    private Category salary;
    private Category otherIncome;
    private Category otherUsersSalary;

    private static final LocalDate AUG_10 = LocalDate.of(2026, 8, 10);
    private static final LocalDate SEP_9 = LocalDate.of(2026, 9, 9);
    private static final LocalDate SEP_12 = LocalDate.of(2026, 9, 12);
    private static final LocalDate SEP_14 = LocalDate.of(2026, 9, 14);
    private static final LocalDate OCT_9 = LocalDate.of(2026, 10, 9);

    @BeforeEach
    void setUp() {
        user = persistUser("anchors@example.com", "anchors");
        otherUser = persistUser("anchors-other@example.com", "anchors-other");
        salaryWallet = persistWallet(user, "Daily", false);
        savingsWallet = persistWallet(user, "Savings", false);
        fundWallet = persistWallet(user, "Fund", true);
        otherUsersWallet = persistWallet(otherUser, "Daily", false);
        salary = persistCategory(user, "Salary", true);
        otherIncome = persistCategory(user, "Gift", false);
        otherUsersSalary = persistCategory(otherUser, "Salary", true);
    }

    @Test
    void findAnchorDates_returnsDistinctDatesNewestFirstForTheGivenWalletCategoryAndUpTo() {
        tx(salaryWallet, salary, AUG_10);
        tx(salaryWallet, salary, SEP_9);
        tx(salaryWallet, salary, SEP_9);           // same-day duplicate collapses
        tx(salaryWallet, salary, SEP_12);
        tx(salaryWallet, salary, OCT_9);           // after upTo → excluded
        tx(salaryWallet, otherIncome, SEP_14);     // other category → excluded
        tx(savingsWallet, salary, SEP_14);         // other wallet → excluded
        tx(otherUsersWallet, otherUsersSalary, SEP_14);
        flushAndClear();

        List<LocalDate> dates = transactionRepository.findAnchorDates(
                salaryWallet.getId(), user.getId(), salary.getId(), SEP_14, Pageable.unpaged());

        assertThat(dates).containsExactly(SEP_12, SEP_9, AUG_10);
    }

    @Test
    void findAnchorDates_honoursTheInclusiveUpToBoundAndPaging() {
        tx(salaryWallet, salary, AUG_10);
        tx(salaryWallet, salary, SEP_9);
        tx(salaryWallet, salary, SEP_12);
        flushAndClear();

        assertThat(transactionRepository.findAnchorDates(
                salaryWallet.getId(), user.getId(), salary.getId(), SEP_9, Pageable.unpaged()))
                .containsExactly(SEP_9, AUG_10);
        assertThat(transactionRepository.findAnchorDates(
                salaryWallet.getId(), user.getId(), salary.getId(), SEP_14, PageRequest.of(0, 2)))
                .containsExactly(SEP_12, SEP_9);
    }

    @Test
    void findAnchorDates_rejectsAWalletThatBelongsToAnotherUser() {
        tx(otherUsersWallet, otherUsersSalary, SEP_9);
        flushAndClear();

        List<LocalDate> dates = transactionRepository.findAnchorDates(
                otherUsersWallet.getId(), user.getId(), otherUsersSalary.getId(), SEP_14, Pageable.unpaged());

        assertThat(dates).isEmpty();
    }

    @Test
    void findLatestAnchorWalletIds_picksTheRegularWalletHoldingTheNewestAnchorTransaction() {
        tx(salaryWallet, salary, AUG_10);
        tx(savingsWallet, salary, SEP_9);          // salary moved to savings → newest anchor wins
        tx(fundWallet, salary, SEP_12);            // fund wallets never qualify
        tx(salaryWallet, otherIncome, SEP_14);     // not an anchor
        tx(otherUsersWallet, otherUsersSalary, SEP_14);
        flushAndClear();

        List<Integer> walletIds = transactionRepository.findLatestAnchorWalletIds(
                user.getId(), salary.getId(), PageRequest.of(0, 1));

        assertThat(walletIds).containsExactly(savingsWallet.getId());
    }

    @Test
    void findLatestAnchorWalletIds_isEmptyWithoutAnchorTransactions() {
        tx(salaryWallet, otherIncome, SEP_14);
        flushAndClear();

        assertThat(transactionRepository.findLatestAnchorWalletIds(user.getId(), salary.getId(), PageRequest.of(0, 1)))
                .isEmpty();
    }

    private User persistUser(String email, String username) {
        return entityManager.persist(User.builder().email(email).username(username).passwordHash("hash").build());
    }

    private Wallet persistWallet(User owner, String name, boolean fund) {
        return entityManager.persist(Wallet.builder().user(owner).name(name).balance(BigDecimal.ZERO).isFund(fund).build());
    }

    private Category persistCategory(User owner, String name, boolean cycleAnchor) {
        return entityManager.persist(Category.builder()
                .user(owner)
                .name(name)
                .type(CategoryType.INCOME)
                .emoji(name.substring(0, 1) + (cycleAnchor ? "!" : "?") + owner.getUsername())
                .isCycleAnchor(cycleAnchor)
                .build());
    }

    private void tx(Wallet wallet, Category category, LocalDate date) {
        entityManager.persist(Transaction.builder()
                .wallet(wallet)
                .category(category)
                .title(UUID.randomUUID().toString().substring(0, 12))
                .amount(new BigDecimal("100.00"))
                .transactionDate(date)
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
