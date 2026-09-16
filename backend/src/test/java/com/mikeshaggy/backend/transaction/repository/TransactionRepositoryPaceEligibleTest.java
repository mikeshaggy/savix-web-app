package com.mikeshaggy.backend.transaction.repository;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
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

/**
 * Stage 4.1/4.2: pace-eligible variable spend. The exclusion flags must only
 * affect the two pace queries — the reporting sums keep counting every row.
 */
@DataJpaTest
class TransactionRepositoryPaceEligibleTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 9);
    private static final LocalDate TO = LocalDate.of(2026, 9, 14);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User user;
    private Wallet wallet;
    private Wallet otherWallet;
    private Category groceries;
    private Category moneyLent;
    private Category salary;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.builder()
                .email("pace-test@example.com")
                .username("pace-test")
                .passwordHash("hash")
                .build());
        wallet = entityManager.persist(Wallet.builder().user(user).name("Daily").balance(BigDecimal.ZERO).build());
        otherWallet = entityManager.persist(Wallet.builder().user(user).name("Savings").balance(BigDecimal.ZERO).build());
        groceries = category("Groceries", CategoryType.EXPENSE, false);
        moneyLent = category("Money lent", CategoryType.EXPENSE, true);
        salary = category("Salary", CategoryType.INCOME, false);
    }

    @Test
    void sumExcludesTransactionFlagAndCategoryFlagButReportingSumsKeepThem() {
        tx(wallet, groceries, "94.98", LocalDate.of(2026, 9, 9), false);
        tx(wallet, groceries, "300.00", LocalDate.of(2026, 9, 13), true);   // transaction override
        tx(wallet, moneyLent, "150.00", LocalDate.of(2026, 9, 12), false);  // category excluded
        flushAndClear();

        BigDecimal pace = transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRange(
                wallet.getId(), user.getId(), FROM, TO);
        BigDecimal unlinked = transactionRepository.sumUnlinkedByWalletUserDateRangeAndType(
                wallet.getId(), user.getId(), FROM, TO, CategoryType.EXPENSE);
        BigDecimal total = transactionRepository.sumByWalletUserDateRangeAndType(
                wallet.getId(), user.getId(), FROM, TO, CategoryType.EXPENSE);

        assertThat(pace).isEqualByComparingTo("94.98");
        assertThat(unlinked).isEqualByComparingTo("544.98");
        assertThat(total).isEqualByComparingTo("544.98");
    }

    @Test
    void sumSkipsLinkedIncomeOtherWalletAndOutOfRangeRows() {
        tx(wallet, groceries, "10.00", FROM, false);
        tx(wallet, groceries, "20.00", TO, false);
        tx(wallet, groceries, "999.00", FROM.minusDays(1), false);
        tx(wallet, groceries, "999.00", TO.plusDays(1), false);
        tx(wallet, salary, "5000.00", LocalDate.of(2026, 9, 10), false);
        tx(otherWallet, groceries, "77.00", LocalDate.of(2026, 9, 10), false);
        Transaction rent = tx(wallet, groceries, "1500.00", LocalDate.of(2026, 9, 10), false);
        link(rent);
        flushAndClear();

        BigDecimal pace = transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRange(
                wallet.getId(), user.getId(), FROM, TO);

        assertThat(pace).isEqualByComparingTo("30.00");
    }

    @Test
    void sumIsZeroNotNullWhenNothingMatches() {
        BigDecimal pace = transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRange(
                wallet.getId(), user.getId(), FROM, TO);

        assertThat(pace).isNotNull().isEqualByComparingTo("0");
    }

    @Test
    void dailyTotalsGroupEligibleRowsByDayAscendingAndSkipExcludedLinkedAndForeignRows() {
        tx(wallet, groceries, "40.00", LocalDate.of(2026, 9, 10), false);
        tx(wallet, groceries, "25.00", LocalDate.of(2026, 9, 10), false);
        tx(wallet, groceries, "50.00", LocalDate.of(2026, 9, 13), false);
        tx(wallet, groceries, "300.00", LocalDate.of(2026, 9, 13), true);   // transaction override
        tx(wallet, moneyLent, "150.00", LocalDate.of(2026, 9, 11), false);  // category excluded
        tx(wallet, salary, "5000.00", LocalDate.of(2026, 9, 9), false);      // income
        tx(otherWallet, groceries, "77.00", LocalDate.of(2026, 9, 12), false);
        tx(wallet, groceries, "999.00", TO.plusDays(1), false);
        Transaction rent = tx(wallet, groceries, "1500.00", LocalDate.of(2026, 9, 9), false);
        link(rent);
        flushAndClear();

        List<DailyTotalProjection> rows = transactionRepository.findDailyPaceEligibleVariableTotals(
                wallet.getId(), user.getId(), FROM, TO);

        assertThat(rows).extracting(DailyTotalProjection::getDay)
                .containsExactly(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13));
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo("65.00");
        assertThat(rows.get(1).getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void dailyTotalsAreEmptyWhenNothingMatches() {
        tx(wallet, moneyLent, "150.00", LocalDate.of(2026, 9, 11), false);
        flushAndClear();

        assertThat(transactionRepository.findDailyPaceEligibleVariableTotals(
                wallet.getId(), user.getId(), FROM, TO)).isEmpty();
    }

    private Category category(String name, CategoryType type, boolean excludedFromPace) {
        return entityManager.persist(Category.builder()
                .user(user)
                .name(name)
                .type(type)
                .excludedFromPace(excludedFromPace)
                .build());
    }

    private Transaction tx(Wallet target, Category category, String amount, LocalDate date, boolean excludedFromPace) {
        return entityManager.persist(Transaction.builder()
                .wallet(target)
                .category(category)
                .title(UUID.randomUUID().toString().substring(0, 12))
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .importance(category.getType() == CategoryType.EXPENSE ? Importance.ESSENTIAL : null)
                .excludedFromPace(excludedFromPace)
                .build());
    }

    private void link(Transaction transaction) {
        FixedPayment rent = entityManager.persist(FixedPayment.builder()
                .wallet(transaction.getWallet())
                .category(transaction.getCategory())
                .title("Rent")
                .amount(transaction.getAmount())
                .anchorDate(transaction.getTransactionDate())
                .cycle(Cycle.MONTHLY)
                .activeFrom(transaction.getTransactionDate())
                .build());
        entityManager.persist(FixedPaymentOccurrence.builder()
                .fixedPayment(rent)
                .dueDate(transaction.getTransactionDate())
                .expectedAmount(transaction.getAmount())
                .paidAmount(transaction.getAmount())
                .status(OccurrenceStatus.PAID)
                .transaction(transaction)
                .build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
