package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.VariableExpense;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 4.6 on H2: the one-off candidate read model ({@code unlinkedExpenses}) sees every unlinked EXPENSE row —
 * excluded ones included, flagged — while linked and INCOME rows never appear; and its {@code excluded} flag is
 * exactly the complement of membership in the Stage 4.2 pace-eligible series, so the two exclusion predicates
 * (JPQL conjunction vs {@code Transaction#isPaceExcluded()}) cannot drift apart unnoticed.
 */
@DataJpaTest
class OneOffCandidateExclusionTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private TestEntityManager entityManager;

    private User user;
    private Wallet wallet;
    private Category groceries;
    private Category moneyLent;
    private Category salary;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.builder().email("one-off@example.com").username("one-off").passwordHash("hash").build());
        wallet = entityManager.persist(Wallet.builder().user(user).name("Daily").balance(BigDecimal.ZERO).build());
        groceries = entityManager.persist(Category.builder().user(user).name("Groceries").type(CategoryType.EXPENSE).build());
        moneyLent = entityManager.persist(Category.builder().user(user).name("Money lent").type(CategoryType.EXPENSE)
                .excludedFromPace(true).build());
        salary = entityManager.persist(Category.builder().user(user).name("Salary").type(CategoryType.INCOME)
                .isCycleAnchor(true).build());
    }

    @Test
    void candidatesIncludeExcludedRowsFlaggedButNeverLinkedOrIncomeRows() {
        Transaction plain = tx(groceries, "94.98", START, false);
        Transaction byCategory = tx(moneyLent, "150.00", LocalDate.of(2026, 9, 12), false);
        Transaction byTransaction = tx(groceries, "300.00", LocalDate.of(2026, 9, 13), true);
        Transaction bothFlags = tx(moneyLent, "20.00", LocalDate.of(2026, 9, 13), true);
        Transaction rent = tx(groceries, "1879.91", LocalDate.of(2026, 9, 10), false);
        link(rent);
        tx(salary, "9444.81", START, false);
        tx(groceries, "999.00", START.minusDays(1), false);   // previous cycle
        tx(groceries, "999.00", TODAY.plusDays(1), false);    // tomorrow
        entityManager.flush();
        entityManager.clear();

        AnalyticsTransactionQueryService query = new AnalyticsTransactionQueryService(transactionRepository);
        List<VariableExpense> candidates = query.unlinkedExpenses(wallet.getId(), user.getId(), START, TODAY);

        assertThat(candidates).extracting(VariableExpense::transactionId)
                .containsExactly(plain.getId(), byCategory.getId(), byTransaction.getId(), bothFlags.getId());
        Map<Long, VariableExpense> byId = candidates.stream()
                .collect(Collectors.toMap(VariableExpense::transactionId, Function.identity()));
        assertThat(byId.get(plain.getId()).excluded()).isFalse();
        assertThat(byId.get(byCategory.getId()).excluded()).isTrue();           // category flag alone
        assertThat(byId.get(byCategory.getId()).categoryName()).isEqualTo("Money lent");
        assertThat(byId.get(byTransaction.getId()).excluded()).isTrue();        // transaction flag alone
        assertThat(byId.get(bothFlags.getId()).excluded()).isTrue();
        assertThat(candidates).extracting(VariableExpense::amount).allMatch(a -> a.scale() == 2);
    }

    @Test
    void excludedFlagIsTheComplementOfThePaceEligibleSeries() {
        tx(groceries, "94.98", START, false);
        tx(moneyLent, "150.00", LocalDate.of(2026, 9, 12), false);
        tx(groceries, "50.00", LocalDate.of(2026, 9, 13), false);
        tx(groceries, "300.00", LocalDate.of(2026, 9, 13), true);
        entityManager.flush();
        entityManager.clear();

        AnalyticsTransactionQueryService query = new AnalyticsTransactionQueryService(transactionRepository);
        List<VariableExpense> candidates = query.unlinkedExpenses(wallet.getId(), user.getId(), START, TODAY);
        List<DailyTotal> daily = query.dailyVariableTotals(wallet.getId(), user.getId(), START, TODAY);

        Map<LocalDate, BigDecimal> eligibleByDay = candidates.stream()
                .filter(c -> !c.excluded())
                .collect(Collectors.toMap(VariableExpense::date, VariableExpense::amount, BigDecimal::add));
        for (DailyTotal day : daily) {
            assertThat(day.amount()).isEqualByComparingTo(eligibleByDay.getOrDefault(day.day(), BigDecimal.ZERO));
        }
        assertThat(candidates.stream().map(VariableExpense::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("594.98");                                       // all unlinked (v1 variable)
        assertThat(query.sumUnlinkedForPace(wallet.getId(), user.getId(), START, TODAY))
                .isEqualByComparingTo("144.98");                                       // pace-eligible only
    }

    private Transaction tx(Category category, String amount, LocalDate date, boolean excludedFromPace) {
        return entityManager.persist(Transaction.builder()
                .wallet(wallet)
                .category(category)
                .title("t-" + amount + "-" + date)
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .importance(category.getType() == CategoryType.EXPENSE ? Importance.ESSENTIAL : null)
                .excludedFromPace(excludedFromPace)
                .build());
    }

    private void link(Transaction transaction) {
        FixedPayment rent = entityManager.persist(FixedPayment.builder()
                .wallet(wallet)
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
}
