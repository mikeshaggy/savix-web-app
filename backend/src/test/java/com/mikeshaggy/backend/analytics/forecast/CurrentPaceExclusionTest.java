package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CurrentPace;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 4.4 end to end on H2: the pace series comes from the Stage 4.1/4.2 query only, so a transaction
 * or category flagged {@code excludedFromPace} never reaches the median — no second exclusion predicate
 * exists in the forecast package.
 */
@DataJpaTest
class CurrentPaceExclusionTest {

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

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.builder().email("pace-v2@example.com").username("pace-v2").passwordHash("hash").build());
        wallet = entityManager.persist(Wallet.builder().user(user).name("Daily").balance(BigDecimal.ZERO).build());
        groceries = entityManager.persist(Category.builder().user(user).name("Groceries").type(CategoryType.EXPENSE).build());
        moneyLent = entityManager.persist(Category.builder().user(user).name("Money lent").type(CategoryType.EXPENSE)
                .excludedFromPace(true).build());
    }

    @Test
    void excludedTransactionAndCategoryNeverEnterTheCurrentPace() {
        tx(groceries, "94.98", START, false);
        tx(moneyLent, "150.00", LocalDate.of(2026, 9, 12), false);   // category excluded
        tx(groceries, "50.00", LocalDate.of(2026, 9, 13), false);
        tx(groceries, "300.00", LocalDate.of(2026, 9, 13), true);    // transaction override
        entityManager.flush();
        entityManager.clear();

        AnalyticsTransactionQueryService query = new AnalyticsTransactionQueryService(transactionRepository);
        List<DailyTotal> daily = query.dailyVariableTotals(wallet.getId(), user.getId(), START, TODAY);
        CurrentPace pace = new ForecastV2Calculator().currentPace(daily, 24);

        assertThat(daily).extracting(d -> d.amount().toPlainString())
                .containsExactly("94.98", "0.00", "0.00", "0.00", "50.00", "0.00");
        assertThat(pace.daysElapsed()).isEqualTo(6);
        assertThat(pace.variableToDate()).isEqualByComparingTo("144.98");
        assertThat(pace.trimmedDailyPace()).isEqualByComparingTo("0.00");       // four zero days outvote two spend days
        assertThat(pace.rawDailyBurnRate()).isEqualByComparingTo("24.16");      // 144.98 / 6
        assertThat(pace.paceProjection()).isEqualByComparingTo("0.00");
    }

    @Test
    void allExcludedTransactionsYieldAnAllZeroSeriesWithDaysElapsedStillEqualToTheWindowLength() {
        tx(moneyLent, "150.00", START, false);                                  // category excluded
        tx(groceries, "999.00", LocalDate.of(2026, 9, 10), true);                // transaction override excluded
        entityManager.flush();
        entityManager.clear();

        AnalyticsTransactionQueryService query = new AnalyticsTransactionQueryService(transactionRepository);
        List<DailyTotal> daily = query.dailyVariableTotals(wallet.getId(), user.getId(), START, TODAY);
        CurrentPace pace = new ForecastV2Calculator().currentPace(daily, 24);

        assertThat(daily).extracting(d -> d.amount().toPlainString())
                .containsExactly("0.00", "0.00", "0.00", "0.00", "0.00", "0.00");
        assertThat(pace.daysElapsed()).isEqualTo(6);   // window length, even though every row is excluded
        assertThat(pace.variableToDate()).isEqualByComparingTo("0.00");
        assertThat(pace.trimmedDailyPace()).isEqualByComparingTo("0.00");
        assertThat(pace.rawDailyBurnRate()).isEqualByComparingTo("0.00");
        assertThat(pace.paceProjection()).isEqualByComparingTo("0.00");
    }

    private void tx(Category category, String amount, LocalDate date, boolean excludedFromPace) {
        entityManager.persist(Transaction.builder()
                .wallet(wallet)
                .category(category)
                .title(UUID.randomUUID().toString().substring(0, 12))
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .importance(Importance.ESSENTIAL)
                .excludedFromPace(excludedFromPace)
                .build());
    }
}
