package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.dashboard.dto.DashboardCycleSoFarDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardSavingsDto;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.transfer.domain.Transfer;
import com.mikeshaggy.backend.transfer.repository.TransferRepository;
import com.mikeshaggy.backend.transfer.service.TransferSummaryService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stage 6.3 tester coverage: the real {@link DashboardV2Sections} over one H2 ledger — the real pace-eligible
 * query, the real {@link TransferSummaryService} and the real wallet repository. Only the pay-cycle windows are
 * stubbed. Proves what the mock-based {@code DashboardV2SectionsTest} cannot: that rows excluded from the pace
 * (on the transaction or on its category) and fixed-linked rows never enter {@code variableSpent}, and that the
 * savings wallet is the single non-fund, non-salary wallet with the 6.2 net.
 */
@DataJpaTest
class DashboardV2SectionsLedgerTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final LocalDate END = LocalDate.of(2026, 10, 8);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);
    private static final Clock CLOCK = Clock.fixed(TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Autowired private TestEntityManager entityManager;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransferRepository transferRepository;
    @Autowired private WalletRepository walletRepository;

    private final PayCycleService payCycleService = mock(PayCycleService.class);

    private User user;
    private Wallet daily;
    private Category groceries;
    private Category rentCategory;
    private DashboardV2Sections sections;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.builder()
                .email("v2-ledger@example.com").username("v2-ledger").passwordHash("hash").build());
        daily = wallet("daily", "5896.89", false);
        groceries = category("Groceries", false);
        rentCategory = category("Rent", false);
        entityManager.flush();

        when(payCycleService.current(user.getId()))
                .thenReturn(Optional.of(PayCycle.open(user.getId(), daily.getId(), START, END.plusDays(1))));
        when(payCycleService.history(eq(user.getId()), anyInt())).thenReturn(List.of());

        AnalyticsTransactionQueryService queries = new AnalyticsTransactionQueryService(transactionRepository);
        sections = new DashboardV2Sections(queries, new HistoricalVariableSpendService(payCycleService, queries),
                new TransferSummaryService(transferRepository, walletRepository, payCycleService, CLOCK),
                walletRepository);
    }

    @Nested
    class VariableSpent {

        @Test
        void excludedFromPaceRowsAndFixedLinkedRowsNeverEnterVariableSpent() {
            Category flaggedCategory = category("One-offs", true);
            tx(groceries, "94.98", START, false);                        // pace-eligible
            tx(groceries, "41.20", TODAY, false);                        // pace-eligible, on the cutoff day
            tx(groceries, "300.00", START.plusDays(2), true);            // excluded on the transaction
            tx(flaggedCategory, "700.00", START.plusDays(3), false);     // excluded on its category
            tx(flaggedCategory, "55.00", START.plusDays(3), true);       // excluded on both
            fixedLinked("1500.00", START.plusDays(1));                   // ledger-linked fixed: its own figure
            tx(groceries, "999.00", TODAY.plusDays(1), false);           // after the cutoff
            tx(groceries, "888.00", START.minusDays(1), false);          // before the cycle
            entityManager.flush();
            entityManager.clear();

            DashboardCycleSoFarDto result = sections.cycleSoFar(
                    walletRepository.findById(daily.getId()).orElseThrow(), user.getId(), open(), TODAY,
                    projection("1500.00"));

            assertThat(result.variableSpent()).isEqualByComparingTo("136.18");
            assertThat(result.fixedSpent()).isEqualByComparingTo("1500.00");
            // no history: typical and delta are absent, never zero
            assertThat(result.typicalVariableByToday()).isNull();
            assertThat(result.deltaVsTypical()).isNull();
        }

        @Test
        void typicalIgnoresExclusionsAndFixedLinkedRowsOfTheClosedCyclesToo() {
            PayCycle closed = PayCycle.closed(user.getId(), daily.getId(), LocalDate.of(2026, 8, 10), START.minusDays(1));
            when(payCycleService.history(eq(user.getId()), anyInt())).thenReturn(List.of(closed));
            Category flaggedCategory = category("One-offs", true);
            tx(groceries, "120.00", closed.start().plusDays(1), false);          // day 2
            tx(groceries, "500.00", closed.start().plusDays(2), true);           // excluded, day 3
            tx(flaggedCategory, "600.00", closed.start().plusDays(2), false);    // excluded by category, day 3
            fixedLinked("1500.00", closed.start().plusDays(3));                  // fixed, day 4
            tx(groceries, "50.00", closed.start().plusDays(4), false);           // day 5
            tx(groceries, "70.00", START.plusDays(1), false);                    // current cycle, day 2
            entityManager.flush();
            entityManager.clear();

            DashboardCycleSoFarDto result = sections.cycleSoFar(
                    walletRepository.findById(daily.getId()).orElseThrow(), user.getId(), open(), TODAY,
                    projection("0.00"));

            assertThat(result.variableSpent()).isEqualByComparingTo("70.00");
            assertThat(result.typicalVariableByToday()).isEqualByComparingTo("170.00");
            assertThat(result.deltaVsTypical()).isEqualByComparingTo("-100.00");
        }
    }

    @Nested
    class Savings {

        private Wallet savings;

        @BeforeEach
        void savingsWallet() {
            savings = wallet("savings", "460.01", false);
        }

        @Test
        void singleNonFundCounterWalletCarriesItsActualBalanceAndTheCurrentCycleNetFromTheSalaryWalletsSide() {
            Wallet fund = wallet("Vacation", "300.00", true);
            transfer(savings, daily, "1000.00", START);                  // savings funded daily: in
            transfer(daily, savings, "400.25", TODAY);                   // daily moved into savings: out
            transfer(savings, daily, "77.00", TODAY.plusDays(1));        // future: not counted
            transfer(savings, daily, "5.00", START.minusDays(1));        // previous cycle
            transfer(daily, fund, "300.00", TODAY.minusDays(1));         // fund deposit: never counted
            transfer(fund, daily, "50.00", TODAY.minusDays(2));          // fund withdrawal: never counted
            entityManager.flush();
            entityManager.clear();

            DashboardSavingsDto result = sections.savings(daily.getId(), user.getId(), open());

            assertThat(result).isNotNull();
            assertThat(result.counterWalletName()).isEqualTo("savings");
            assertThat(result.balance()).isEqualByComparingTo("460.01");
            assertThat(result.netFlowThisCycle()).isEqualByComparingTo("599.75");
        }

        @Test
        void moneyMovedIntoSavingsIsANegativeNet() {
            transfer(daily, savings, "250.00", START.plusDays(1));
            entityManager.flush();
            entityManager.clear();
            assertThat(sections.savings(daily.getId(), user.getId(), open()).netFlowThisCycle())
                    .isEqualByComparingTo("-250.00");

        }

        @Test
        void noTransfersThisCycleGivesZeroNetAndTheBalance() {
            transfer(savings, daily, "10.00", START.minusDays(3));       // only a previous-cycle transfer
            entityManager.flush();
            entityManager.clear();

            DashboardSavingsDto result = sections.savings(daily.getId(), user.getId(), open());

            assertThat(result.netFlowThisCycle()).isEqualByComparingTo("0.00");
            assertThat(result.balance()).isEqualByComparingTo("460.01");
        }

        @Test
        void aSecondNonFundNonSalaryWalletMakesItAmbiguousAndGivesNoSavings() {
            wallet("travel", "10.00", false);
            entityManager.flush();
            entityManager.clear();

            assertThat(sections.savings(daily.getId(), user.getId(), open())).isNull();
        }

        @Test
        void onlyFundWalletsBesidesTheSalaryWalletGivesNoSavings() {
            // replace the savings wallet by making the user's only other wallet a fund
            savings.setFund(true);
            entityManager.flush();
            entityManager.clear();

            assertThat(sections.savings(daily.getId(), user.getId(), open())).isNull();
        }

        @Test
        void anotherUsersWalletsAreNeverCandidates() {
            User other = entityManager.persist(User.builder()
                    .email("v2-other@example.com").username("v2-other").passwordHash("hash").build());
            entityManager.persist(Wallet.builder().user(other).name("theirs").balance(new BigDecimal("1.00")).build());
            entityManager.flush();
            entityManager.clear();

            DashboardSavingsDto result = sections.savings(daily.getId(), user.getId(), open());

            assertThat(result).isNotNull();
            assertThat(result.counterWalletName()).isEqualTo("savings");
        }
    }

    private static PeriodDto open() {
        return new PeriodDto(START, END, END.plusDays(1), PeriodType.PAY_CYCLE, CycleState.OPEN, END.plusDays(1), true);
    }

    /** Only {@code linkedFixedExpensesToDate} is read by the section. */
    private static SpendingProjectionDto projection(String linkedFixed) {
        return new SpendingProjectionDto(PeriodType.PAY_CYCLE, "Current pay cycle", START, END, 30, 6, 24,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO, null, null,
                BigDecimal.ZERO, new BigDecimal(linkedFixed), null, null, true, null, null);
    }

    private Wallet wallet(String name, String balance, boolean isFund) {
        return entityManager.persist(Wallet.builder()
                .user(user).name(name).balance(new BigDecimal(balance)).isFund(isFund).build());
    }

    private Category category(String name, boolean excludedFromPace) {
        return entityManager.persist(Category.builder()
                .user(user).name(name).type(CategoryType.EXPENSE).excludedFromPace(excludedFromPace).build());
    }

    private Transfer transfer(Wallet from, Wallet to, String amount, LocalDate date) {
        return entityManager.persist(Transfer.builder()
                .fromWallet(from).toWallet(to).amount(new BigDecimal(amount)).transferDate(date).build());
    }

    private Transaction tx(Category category, String amount, LocalDate date, boolean excludedFromPace) {
        return entityManager.persist(Transaction.builder()
                .wallet(daily)
                .category(category)
                .title(UUID.randomUUID().toString().substring(0, 12))
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .importance(Importance.ESSENTIAL)
                .excludedFromPace(excludedFromPace)
                .build());
    }

    private void fixedLinked(String amount, LocalDate date) {
        Transaction transaction = tx(rentCategory, amount, date, false);
        FixedPayment rent = entityManager.persist(FixedPayment.builder()
                .wallet(daily)
                .category(rentCategory)
                .title("Rent " + date)
                .amount(transaction.getAmount())
                .anchorDate(date)
                .cycle(Cycle.MONTHLY)
                .activeFrom(date)
                .build());
        entityManager.persist(FixedPaymentOccurrence.builder()
                .fixedPayment(rent)
                .dueDate(date)
                .expectedAmount(transaction.getAmount())
                .paidAmount(transaction.getAmount())
                .status(OccurrenceStatus.PAID)
                .transaction(transaction)
                .build());
    }
}
