package com.mikeshaggy.backend.analytics;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonResponseDto;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonService;
import com.mikeshaggy.backend.analytics.forecast.ForecastService;
import com.mikeshaggy.backend.analytics.forecast.ForecastShadowObserver;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.analytics.insight.InsightResponseDto;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.budget.repository.CategoryBudgetRepository;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.repository.CategoryRepository;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.dashboard.dto.DashboardCycleSoFarDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardSummaryDto;
import com.mikeshaggy.backend.dashboard.service.DashboardSummaryService;
import com.mikeshaggy.backend.dashboard.service.DashboardV2Sections;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedProgressDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.RiskIndicatorDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.transfer.repository.TransferRepository;
import com.mikeshaggy.backend.transfer.service.TransferSummaryService;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression safety across analytics surfaces (Stage 6.4 rewrite; the subject was the deleted Analytics Overview).
 * <p>
 * Invariant: for the same wallet, the same pay cycle and the same cycle day, the dashboard's {@code cycleSoFar}
 * totals ({@code dashboard-v2}) agree with {@link CycleComparisonService}'s current-cycle total — every expense of the
 * cycle so far is either fixed-linked ({@code fixedSpent}), pace-eligible variable ({@code variableSpent}) or excluded
 * from the pace (in neither, by the Stage 4.1 rule), and nothing else. Both sides read one H2 ledger through the real
 * query services; only the pay-cycle windows, the fixed-payment tile and the insight engine are stubbed.
 */
@DataJpaTest
class AnalyticsRegressionSafetyTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final LocalDate END = LocalDate.of(2026, 10, 8);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 14);
    private static final Clock CLOCK = Clock.fixed(TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final FeatureFlags DASHBOARD_V2 = new FeatureFlags(true, false, false, true, false);

    @Autowired private TestEntityManager entityManager;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransferRepository transferRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private CategoryRepository categoryRepository;

    private final PayCycleService payCycleService = mock(PayCycleService.class);
    private final PeriodService periodService = mock(PeriodService.class);
    private final WalletService walletService = mock(WalletService.class);
    private final FixedPaymentDashboardService fixedPaymentDashboardService = mock(FixedPaymentDashboardService.class);
    private final InsightEngine insightEngine = mock(InsightEngine.class);
    private final CategoryAggregationService categoryAggregationService = mock(CategoryAggregationService.class);

    private User user;
    private Wallet daily;
    private Category groceries;
    private Category rentCategory;
    private Category salary;
    private DashboardSummaryService dashboard;
    private CycleComparisonService comparison;

    @BeforeEach
    void setUp() {
        user = entityManager.persist(User.builder()
                .email("regression-safety@example.com").username("regression-safety").passwordHash("hash").build());
        daily = entityManager.persist(Wallet.builder().user(user).name("daily").balance(new BigDecimal("5896.89")).build());
        groceries = category("Groceries", CategoryType.EXPENSE);
        rentCategory = category("Rent", CategoryType.EXPENSE);
        salary = category("Salary", CategoryType.INCOME);

        // the current cycle, day 1 … 6 (TODAY)
        tx(salary, "6386.81", START, false);
        tx(groceries, "94.98", START, false);
        fixedLinked("1500.00", START.plusDays(1));
        tx(groceries, "335.64", START.plusDays(2), false);
        tx(groceries, "41.20", TODAY, false);
        tx(groceries, "999.00", TODAY.plusDays(1), false);            // future-dated: outside every window
        // closed cycles (baseline side of both services)
        PayCycle c1 = PayCycle.closed(user.getId(), daily.getId(), LocalDate.of(2026, 8, 10), START.minusDays(1));
        PayCycle c2 = PayCycle.closed(user.getId(), daily.getId(), LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9));
        tx(groceries, "120.00", c1.start().plusDays(1), false);
        fixedLinked("1500.00", c1.start().plusDays(1));
        tx(groceries, "210.00", c2.start().plusDays(3), false);
        entityManager.flush();
        entityManager.clear();

        when(payCycleService.current(user.getId()))
                .thenReturn(Optional.of(PayCycle.open(user.getId(), daily.getId(), START, END.plusDays(1))));
        when(payCycleService.history(eq(user.getId()), anyInt())).thenReturn(List.of(c1, c2));
        Wallet wallet = walletRepository.findById(daily.getId()).orElseThrow();
        when(walletService.getWalletEntityByIdForUser(daily.getId(), user.getId())).thenReturn(wallet);
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, daily.getId(), user.getId(), null, null))
                .thenReturn(new ResolvedPeriods(new PeriodDto(START, END, END.plusDays(1), PeriodType.PAY_CYCLE,
                        CycleState.OPEN, END.plusDays(1), true), null));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(any(), any(Wallet.class), any(), any())).thenReturn(emptyTile());
        when(insightEngine.getInsightsForWindow(any(), any(), any(), any(), any(), any()))
                .thenReturn(new InsightResponseDto(START, TODAY, List.of()));
        when(categoryAggregationService.aggregateExpenses(any(), any(), any(), any(), any()))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));

        AnalyticsTransactionQueryService queries = new AnalyticsTransactionQueryService(transactionRepository);
        SpendingProjectionService projections = new SpendingProjectionService(queries, walletService, periodService,
                fixedPaymentDashboardService, new SpendingProjectionCalculator(), mock(ForecastService.class),
                mock(ForecastShadowObserver.class), DASHBOARD_V2, CLOCK);
        DashboardV2Sections v2Sections = new DashboardV2Sections(queries,
                new HistoricalVariableSpendService(payCycleService, queries),
                new TransferSummaryService(transferRepository, walletRepository, payCycleService, CLOCK),
                walletRepository);
        dashboard = new DashboardSummaryService(periodService, walletService, queries, projections,
                mock(ForecastShadowObserver.class), fixedPaymentDashboardService, insightEngine,
                categoryAggregationService, mock(CategoryBudgetRepository.class), v2Sections, DASHBOARD_V2, CLOCK);
        comparison = new CycleComparisonService(transactionRepository, walletService, categoryRepository, CLOCK,
                DASHBOARD_V2, payCycleService);
    }

    @Test
    void cycleSoFarTotalsAgreeWithCycleComparisonOnEveryDayOfTheCycleSoFar() {
        for (LocalDate asOf = START; !asOf.isAfter(TODAY); asOf = asOf.plusDays(1)) {
            DashboardSummaryDto summary = dashboard(asOf);
            CycleComparisonResponseDto compared = comparison(asOf);
            DashboardCycleSoFarDto cycleSoFar = summary.cycleSoFar();

            // same cycle, same cycle day
            assertThat(summary.period().startDate()).as("start at %s", asOf).isEqualTo(compared.currentCycle().startDate());
            assertThat(summary.period().daysElapsed()).as("day at %s", asOf).isEqualTo(compared.currentCycle().dayIndex());
            // fixed + variable = the comparison's current-cycle total = the dashboard's reporting expenses
            assertThat(cycleSoFar.fixedSpent().add(cycleSoFar.variableSpent()))
                    .as("cycle so far at %s", asOf)
                    .isEqualByComparingTo(compared.summary().currentExpenses())
                    .isEqualByComparingTo(summary.kpis().expenses().amount());
        }
    }

    @Test
    void fixtureTotalsAtTodayArePinned() {
        DashboardCycleSoFarDto cycleSoFar = dashboard(TODAY).cycleSoFar();
        CycleComparisonResponseDto compared = comparison(TODAY);

        assertThat(cycleSoFar.fixedSpent()).isEqualByComparingTo("1500.00");
        assertThat(cycleSoFar.variableSpent()).isEqualByComparingTo("471.82");
        assertThat(compared.summary().currentExpenses()).isEqualByComparingTo("1971.82");
        assertThat(compared.currentCycle().dayIndex()).isEqualTo(6);
        // Stage 4 baseline at day 6 over the two closed cycles: 120.00 and 210.00 → median 165.00
        assertThat(cycleSoFar.typicalVariableByToday()).isEqualByComparingTo("165.00");
        assertThat(cycleSoFar.deltaVsTypical()).isEqualByComparingTo("306.82");
    }

    @Test
    void aPaceExclusionMovesSpendOutOfVariableButNeverOutOfTheComparisonTotal() {
        tx(groceries, "300.00", START.plusDays(3), true);             // one-off excluded from the pace
        entityManager.flush();
        entityManager.clear();

        DashboardSummaryDto summary = dashboard(TODAY);
        CycleComparisonResponseDto compared = comparison(TODAY);
        DashboardCycleSoFarDto cycleSoFar = summary.cycleSoFar();

        assertThat(cycleSoFar.variableSpent()).isEqualByComparingTo("471.82");
        assertThat(cycleSoFar.fixedSpent()).isEqualByComparingTo("1500.00");
        assertThat(compared.summary().currentExpenses()).isEqualByComparingTo("2271.82");
        assertThat(summary.kpis().expenses().amount()).isEqualByComparingTo("2271.82");
        // the gap is exactly the excluded amount — no drift between the two services
        assertThat(compared.summary().currentExpenses()
                .subtract(cycleSoFar.fixedSpent())
                .subtract(cycleSoFar.variableSpent()))
                .isEqualByComparingTo("300.00");
    }

    private DashboardSummaryDto dashboard(LocalDate asOf) {
        return dashboard.getSummary(daily.getId(), user.getId(), PeriodType.PAY_CYCLE, null, null, asOf,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);
    }

    private CycleComparisonResponseDto comparison(LocalDate asOf) {
        return comparison.getCycleComparison(daily.getId(), user.getId(), asOf, 3, null,
                CategoryAggregationMode.ALL, null);
    }

    private Category category(String name, CategoryType type) {
        return entityManager.persist(Category.builder().user(user).name(name).type(type).build());
    }

    private Transaction tx(Category category, String amount, LocalDate date, boolean excludedFromPace) {
        return entityManager.persist(Transaction.builder()
                .wallet(daily)
                .category(category)
                .title(UUID.randomUUID().toString().substring(0, 12))
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .importance(category.getType() == CategoryType.EXPENSE ? Importance.ESSENTIAL : null)
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

    private static FixedTransactionsTileDto emptyTile() {
        return new FixedTransactionsTileDto(START, END, END, null, null,
                new FixedSummaryDto(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0,
                        BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO),
                new FixedProgressDto(0, 0, BigDecimal.ZERO, null, null, null, null, 0),
                BigDecimal.ZERO, BigDecimal.ZERO, new RiskIndicatorDto(false, null),
                List.of(), List.of(), List.of(), null, List.of());
    }
}
