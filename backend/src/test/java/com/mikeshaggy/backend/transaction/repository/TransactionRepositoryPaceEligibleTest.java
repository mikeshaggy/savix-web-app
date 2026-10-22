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
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

    // ---- Stage 7.3: category split of the daily series and the SHOULDNT_HAVE part of the pace-eligible sum ----

    @Test
    void dailyCategoryTotals_groupByDayAndCategory_ascending_andSkipEveryIneligibleRow() {
        Category restaurants = category("Restaurants", CategoryType.EXPENSE, false);
        tx(wallet, groceries, "40.00", LocalDate.of(2026, 9, 10), false);
        tx(wallet, groceries, "25.50", LocalDate.of(2026, 9, 10), false);
        tx(wallet, restaurants, "60.00", LocalDate.of(2026, 9, 10), false);
        tx(wallet, groceries, "50.00", LocalDate.of(2026, 9, 13), false);
        tx(wallet, groceries, "300.00", LocalDate.of(2026, 9, 13), true);   // transaction override
        tx(wallet, moneyLent, "150.00", LocalDate.of(2026, 9, 11), false);  // category excluded
        tx(wallet, salary, "5000.00", LocalDate.of(2026, 9, 9), false);      // income
        tx(otherWallet, groceries, "77.00", LocalDate.of(2026, 9, 12), false);
        tx(wallet, groceries, "999.00", FROM.minusDays(1), false);
        tx(wallet, groceries, "999.00", TO.plusDays(1), false);
        link(tx(wallet, groceries, "1500.00", LocalDate.of(2026, 9, 9), false));
        flushAndClear();

        List<DailyCategorySpendProjection> rows = transactionRepository.findDailyPaceEligibleCategoryTotals(
                wallet.getId(), user.getId(), FROM, TO);

        assertThat(rows).extracting(DailyCategorySpendProjection::getDate, DailyCategorySpendProjection::getCategoryId)
                .containsExactly(
                        tuple(LocalDate.of(2026, 9, 10), groceries.getId()),
                        tuple(LocalDate.of(2026, 9, 10), restaurants.getId()),
                        tuple(LocalDate.of(2026, 9, 13), groceries.getId()));
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo("65.50");
        assertThat(rows.get(0).getName()).isEqualTo("Groceries");
        assertThat(rows.get(1).getAmount()).isEqualByComparingTo("60.00");
        assertThat(rows.get(2).getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void dailyCategoryTotals_rangeIsInclusiveOnBothEnds_andScopedToTheOwner() {
        tx(wallet, groceries, "10.00", FROM, false);
        tx(wallet, groceries, "20.00", TO, false);
        flushAndClear();

        assertThat(transactionRepository.findDailyPaceEligibleCategoryTotals(wallet.getId(), user.getId(), FROM, TO))
                .extracting(DailyCategorySpendProjection::getDate).containsExactly(FROM, TO);
        // the same wallet id with someone else's user id yields nothing
        assertThat(transactionRepository.findDailyPaceEligibleCategoryTotals(
                wallet.getId(), UUID.randomUUID(), FROM, TO)).isEmpty();
        assertThat(transactionRepository.findDailyPaceEligibleCategoryTotals(
                otherWallet.getId(), user.getId(), FROM, TO)).isEmpty();
    }

    @Test
    void dailyCategoryTotals_addUpToTheDailyTotalsOfTheSameEligibleSet() {
        Category restaurants = category("Restaurants", CategoryType.EXPENSE, false);
        tx(wallet, groceries, "40.00", LocalDate.of(2026, 9, 10), false);
        tx(wallet, restaurants, "60.00", LocalDate.of(2026, 9, 10), false);
        tx(wallet, restaurants, "12.34", LocalDate.of(2026, 9, 12), false);
        tx(wallet, groceries, "300.00", LocalDate.of(2026, 9, 12), true);
        tx(wallet, moneyLent, "150.00", LocalDate.of(2026, 9, 12), false);
        link(tx(wallet, groceries, "1500.00", LocalDate.of(2026, 9, 11), false));
        flushAndClear();

        Map<LocalDate, BigDecimal> byCategory = new TreeMap<>();
        transactionRepository.findDailyPaceEligibleCategoryTotals(wallet.getId(), user.getId(), FROM, TO)
                .forEach(row -> byCategory.merge(row.getDate(), row.getAmount(), BigDecimal::add));
        Map<LocalDate, BigDecimal> daily = new TreeMap<>();
        transactionRepository.findDailyPaceEligibleVariableTotals(wallet.getId(), user.getId(), FROM, TO)
                .forEach(row -> daily.put(row.getDay(), row.getAmount()));

        assertThat(byCategory.keySet()).isEqualTo(daily.keySet());
        byCategory.forEach((day, amount) -> assertThat(amount).as(day.toString()).isEqualByComparingTo(daily.get(day)));
    }

    @Test
    void importanceSum_countsOnlyTheRequestedClass_ofThePaceEligibleSet() {
        tx(wallet, groceries, "100.00", LocalDate.of(2026, 9, 10), false, Importance.SHOULDNT_HAVE);
        tx(wallet, groceries, "55.55", LocalDate.of(2026, 9, 12), false, Importance.SHOULDNT_HAVE);
        tx(wallet, groceries, "70.00", LocalDate.of(2026, 9, 12), false, Importance.NICE_TO_HAVE);
        tx(wallet, groceries, "400.00", LocalDate.of(2026, 9, 13), true, Importance.SHOULDNT_HAVE);    // transaction override
        tx(wallet, moneyLent, "250.00", LocalDate.of(2026, 9, 11), false, Importance.SHOULDNT_HAVE);   // category excluded
        tx(otherWallet, groceries, "90.00", LocalDate.of(2026, 9, 11), false, Importance.SHOULDNT_HAVE);
        tx(wallet, groceries, "999.00", FROM.minusDays(1), false, Importance.SHOULDNT_HAVE);
        tx(wallet, groceries, "999.00", TO.plusDays(1), false, Importance.SHOULDNT_HAVE);
        link(tx(wallet, groceries, "1500.00", LocalDate.of(2026, 9, 9), false, Importance.SHOULDNT_HAVE));
        flushAndClear();

        BigDecimal impulse = transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRangeAndImportance(
                wallet.getId(), user.getId(), FROM, TO, Importance.SHOULDNT_HAVE);
        BigDecimal niceToHave = transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRangeAndImportance(
                wallet.getId(), user.getId(), FROM, TO, Importance.NICE_TO_HAVE);
        BigDecimal eligible = transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRange(
                wallet.getId(), user.getId(), FROM, TO);

        assertThat(impulse).isEqualByComparingTo("155.55");
        assertThat(niceToHave).isEqualByComparingTo("70.00");
        assertThat(eligible).isEqualByComparingTo("225.55");
        assertThat(impulse).isLessThanOrEqualTo(eligible);
    }

    @Test
    void importanceSum_isInclusiveOnBothEnds_zeroNotNull_andOwnerScoped() {
        BigDecimal none = transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRangeAndImportance(
                wallet.getId(), user.getId(), FROM, TO, Importance.SHOULDNT_HAVE);
        assertThat(none).isNotNull().isEqualByComparingTo("0");

        tx(wallet, groceries, "10.00", FROM, false, Importance.SHOULDNT_HAVE);
        tx(wallet, groceries, "20.00", TO, false, Importance.SHOULDNT_HAVE);
        flushAndClear();

        assertThat(transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRangeAndImportance(
                wallet.getId(), user.getId(), FROM, TO, Importance.SHOULDNT_HAVE)).isEqualByComparingTo("30.00");
        assertThat(transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRangeAndImportance(
                wallet.getId(), UUID.randomUUID(), FROM, TO, Importance.SHOULDNT_HAVE)).isEqualByComparingTo("0");
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
        return tx(target, category, amount, date, excludedFromPace,
                category.getType() == CategoryType.EXPENSE ? Importance.ESSENTIAL : null);
    }

    private Transaction tx(Wallet target, Category category, String amount, LocalDate date, boolean excludedFromPace,
                           Importance importance) {
        return entityManager.persist(Transaction.builder()
                .wallet(target)
                .category(category)
                .title(UUID.randomUUID().toString().substring(0, 12))
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .importance(importance)
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
