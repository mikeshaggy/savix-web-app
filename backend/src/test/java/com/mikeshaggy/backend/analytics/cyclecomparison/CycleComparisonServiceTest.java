package com.mikeshaggy.backend.analytics.cyclecomparison;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.repository.CategoryRepository;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.regression.September2026Fixture;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.DailyCategorySpendProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CycleComparisonServiceTest {

    private static final Integer WALLET_ID = 1;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Integer ANCHOR_CATEGORY_ID = 99;
    private static final LocalDate AS_OF = LocalDate.of(2026, 5, 17);
    private static final Clock CLOCK = Clock.fixed(
            AS_OF.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletService walletService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private PayCycleService payCycleService;

    private static final FeatureFlags FLAG_OFF = new FeatureFlags(false, false, false, false, false);
    private static final FeatureFlags FLAG_ON = new FeatureFlags(true, false, false, false, false);

    private CycleComparisonService service;

    @BeforeEach
    void setUp() {
        service = new CycleComparisonService(transactionRepository, walletService, categoryRepository, CLOCK,
                FLAG_OFF, payCycleService);
        lenient().when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).build());
    }

    private CycleComparisonService v2Service(Clock clock) {
        return new CycleComparisonService(transactionRepository, walletService, categoryRepository, clock,
                FLAG_ON, payCycleService);
    }

    @Test
    void legacyPathLabelsAnchorBaselineAsPayCycle_andNeverTouchesPayCycleService() {
        givenAnchorDates(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1));
        givenRows(List.of());

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, AS_OF, 1, null, CategoryAggregationMode.ALL, null);

        assertThat(result.baseline().kind()).isEqualTo(CycleComparisonBaselineKind.PAY_CYCLE);
        verifyNoInteractions(payCycleService);
    }

    @Test
    void legacyPathLabelsCalendarMonthsAsMonthlyFallback() {
        when(categoryRepository.findByUserIdAndIsCycleAnchorTrue(USER_ID)).thenReturn(Optional.empty());
        givenRows(List.of());

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, AS_OF, 1, null, CategoryAggregationMode.ALL, null);

        assertThat(result.baseline().kind()).isEqualTo(CycleComparisonBaselineKind.MONTHLY_FALLBACK);
        verifyNoInteractions(payCycleService);
    }

    @Test
    void v2_baselineWindowsAreTheActualClosedCycles() {
        // September fixture: today Sep 14, open cycle Sep 9 → Oct 8, six closed cycles before it.
        UUID userId = September2026Fixture.USER_ID;
        Integer walletId = September2026Fixture.SALARY_WALLET_ID;
        when(walletService.getWalletEntityByIdForUser(walletId, userId))
                .thenReturn(Wallet.builder().id(walletId).build());
        when(payCycleService.current(userId)).thenReturn(Optional.of(
                PayCycle.open(userId, walletId, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));
        when(payCycleService.history(userId, 6)).thenReturn(List.of(
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8)),
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9)),
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9)),
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 9)),
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 7)),
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 9))));
        when(transactionRepository.findDailyCategorySpendByWalletUserDateRangeAndType(
                eq(walletId), eq(userId), eq(LocalDate.of(2026, 3, 10)), eq(September2026Fixture.TODAY),
                eq(CategoryType.EXPENSE), anyList(), anyBoolean(), anyBoolean(), anyList(), anyBoolean()))
                .thenReturn(List.of());

        CycleComparisonResponseDto result = v2Service(September2026Fixture.CLOCK).getCycleComparison(
                walletId, userId, September2026Fixture.TODAY, 6, null, CategoryAggregationMode.ALL, null);

        assertThat(result.currentCycle().startDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(result.currentCycle().endDate()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(result.currentCycle().cutoffDate()).isEqualTo(September2026Fixture.TODAY);
        assertThat(result.currentCycle().dayIndex()).isEqualTo(6);
        assertThat(result.currentCycle().totalDays()).isEqualTo(30);
        assertThat(result.baseline().kind()).isEqualTo(CycleComparisonBaselineKind.PAY_CYCLE);
        assertThat(result.baseline().cyclesUsed()).isEqualTo(6);
        assertThat(result.baseline().cycles())
                .extracting(CycleComparisonBaselineCycleDto::startDate, CycleComparisonBaselineCycleDto::endDate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8)),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9)),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9)),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 9)),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 7)),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 9)));
        assertThat(result.baseline().cycles()).allSatisfy(cycle -> {
            assertThat(cycle.totalDays()).isGreaterThanOrEqualTo(28);
            assertThat(cycle.cutoffDate()).isEqualTo(cycle.startDate().plusDays(5));
        });
        verify(payCycleService).current(userId);
        verify(payCycleService).history(userId, 6);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void v2_historicalAsOfDateSelectsTheClosedCycleContainingIt() {
        // Comparison page for LAST_PAY_CYCLE sends asOfDate = Sep 8 (the last cycle's end) while today is Sep 14.
        UUID userId = September2026Fixture.USER_ID;
        Integer walletId = September2026Fixture.SALARY_WALLET_ID;
        LocalDate asOf = LocalDate.of(2026, 9, 8);
        when(walletService.getWalletEntityByIdForUser(walletId, userId))
                .thenReturn(Wallet.builder().id(walletId).build());
        when(payCycleService.current(userId)).thenReturn(Optional.of(
                PayCycle.open(userId, walletId, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));
        when(payCycleService.history(userId, Integer.MAX_VALUE)).thenReturn(List.of(
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8)),
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9)),
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9)),
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 9)),
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 7))));
        when(transactionRepository.findDailyCategorySpendByWalletUserDateRangeAndType(
                eq(walletId), eq(userId), eq(LocalDate.of(2026, 5, 8)), eq(asOf),
                eq(CategoryType.EXPENSE), anyList(), anyBoolean(), anyBoolean(), anyList(), anyBoolean()))
                .thenReturn(List.of());

        CycleComparisonResponseDto result = v2Service(September2026Fixture.CLOCK).getCycleComparison(
                walletId, userId, asOf, 3, null, CategoryAggregationMode.ALL, null);

        assertThat(result.asOfDate()).isEqualTo(asOf);
        assertThat(result.currentCycle().startDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(result.currentCycle().endDate()).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(result.currentCycle().cutoffDate()).isEqualTo(asOf);
        assertThat(result.currentCycle().dayIndex()).isEqualTo(30);
        assertThat(result.baseline().kind()).isEqualTo(CycleComparisonBaselineKind.PAY_CYCLE);
        assertThat(result.baseline().cycles())
                .extracting(CycleComparisonBaselineCycleDto::startDate, CycleComparisonBaselineCycleDto::endDate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9)),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9)),
                        org.assertj.core.groups.Tuple.tuple(LocalDate.of(2026, 5, 8), LocalDate.of(2026, 6, 9)));
        verify(payCycleService).current(userId);
        verify(payCycleService).history(userId, Integer.MAX_VALUE);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void v2_asOfDateBeforeEveryKnownCycleFallsBackToCalendarMonths() {
        UUID userId = September2026Fixture.USER_ID;
        Integer walletId = September2026Fixture.SALARY_WALLET_ID;
        LocalDate asOf = LocalDate.of(2025, 6, 15);
        when(walletService.getWalletEntityByIdForUser(walletId, userId))
                .thenReturn(Wallet.builder().id(walletId).build());
        when(payCycleService.current(userId)).thenReturn(Optional.of(
                PayCycle.open(userId, walletId, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));
        when(payCycleService.history(userId, Integer.MAX_VALUE)).thenReturn(List.of(
                PayCycle.closed(userId, walletId, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8))));
        when(transactionRepository.findDailyCategorySpendByWalletUserDateRangeAndType(
                eq(walletId), eq(userId), eq(LocalDate.of(2025, 5, 1)), eq(asOf),
                eq(CategoryType.EXPENSE), anyList(), anyBoolean(), anyBoolean(), anyList(), anyBoolean()))
                .thenReturn(List.of());

        CycleComparisonResponseDto result = v2Service(September2026Fixture.CLOCK).getCycleComparison(
                walletId, userId, asOf, 1, null, CategoryAggregationMode.ALL, null);

        assertThat(result.baseline().kind()).isEqualTo(CycleComparisonBaselineKind.MONTHLY_FALLBACK);
        assertThat(result.currentCycle().startDate()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(result.baseline().cycles().getFirst().startDate()).isEqualTo(LocalDate.of(2025, 5, 1));
    }

    @Test
    void v2_nonSalaryWalletGetsCalendarMonthsLabelledNotSalaryWallet() {
        UUID userId = September2026Fixture.USER_ID;
        Integer savings = September2026Fixture.SAVINGS_WALLET_ID;
        when(walletService.getWalletEntityByIdForUser(savings, userId))
                .thenReturn(Wallet.builder().id(savings).build());
        when(payCycleService.current(userId)).thenReturn(Optional.of(PayCycle.open(
                userId, September2026Fixture.SALARY_WALLET_ID, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 9))));
        when(transactionRepository.findDailyCategorySpendByWalletUserDateRangeAndType(
                eq(savings), eq(userId), any(LocalDate.class), any(LocalDate.class),
                eq(CategoryType.EXPENSE), anyList(), anyBoolean(), anyBoolean(), anyList(), anyBoolean()))
                .thenReturn(List.of());

        CycleComparisonResponseDto result = v2Service(September2026Fixture.CLOCK).getCycleComparison(
                savings, userId, September2026Fixture.TODAY, 2, null, CategoryAggregationMode.ALL, null);

        assertThat(result.baseline().kind()).isEqualTo(CycleComparisonBaselineKind.NOT_SALARY_WALLET);
        assertThat(result.currentCycle().startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(result.currentCycle().endDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(result.baseline().cycles().getFirst().startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        verify(payCycleService).current(userId);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void v2_noCycleAtAllGetsCalendarMonthsLabelledMonthlyFallback() {
        UUID userId = September2026Fixture.USER_ID;
        Integer walletId = September2026Fixture.SALARY_WALLET_ID;
        when(walletService.getWalletEntityByIdForUser(walletId, userId))
                .thenReturn(Wallet.builder().id(walletId).build());
        when(payCycleService.current(userId)).thenReturn(Optional.empty());
        when(transactionRepository.findDailyCategorySpendByWalletUserDateRangeAndType(
                eq(walletId), eq(userId), any(LocalDate.class), any(LocalDate.class),
                eq(CategoryType.EXPENSE), anyList(), anyBoolean(), anyBoolean(), anyList(), anyBoolean()))
                .thenReturn(List.of());

        CycleComparisonResponseDto result = v2Service(September2026Fixture.CLOCK).getCycleComparison(
                walletId, userId, September2026Fixture.TODAY, 1, null, CategoryAggregationMode.ALL, null);

        assertThat(result.baseline().kind()).isEqualTo(CycleComparisonBaselineKind.MONTHLY_FALLBACK);
        assertThat(result.currentCycle().startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void currentCycleDaySeventeenComparesAgainstFirstSeventeenDaysOfPreviousCycles() {
        givenAnchorDates(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 2, 1));
        givenRows(List.of(
                row(LocalDate.of(2026, 5, 1), 1, "Groceries", "G", "10.00", 1L),
                row(LocalDate.of(2026, 5, 17), 1, "Groceries", "G", "20.00", 1L),
                row(LocalDate.of(2026, 4, 1), 1, "Groceries", "G", "100.00", 1L),
                row(LocalDate.of(2026, 4, 17), 1, "Groceries", "G", "70.00", 1L),
                row(LocalDate.of(2026, 4, 18), 1, "Groceries", "G", "999.00", 1L),
                row(LocalDate.of(2026, 3, 17), 1, "Groceries", "G", "170.00", 1L),
                row(LocalDate.of(2026, 2, 17), 1, "Groceries", "G", "260.00", 1L)));

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, AS_OF, 3, null, CategoryAggregationMode.ALL, null);

        assertThat(result.currentCycle().startDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(result.currentCycle().endDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(result.currentCycle().dayIndex()).isEqualTo(17);
        assertThat(result.summary().currentExpenses()).isEqualByComparingTo("30.00");
        assertThat(result.baseline().cyclesUsed()).isEqualTo(3);
        assertThat(result.baseline().cycles().getFirst().cutoffDate()).isEqualTo(LocalDate.of(2026, 4, 17));
        assertThat(result.baseline().cycles().getFirst().expensesAtCutoff()).isEqualByComparingTo("170.00");
        assertThat(result.summary().baselineAverageExpenses()).isEqualByComparingTo("200.00");
        assertThat(result.summary().deltaAmount()).isEqualByComparingTo("-170.00");
        assertThat(result.summary().deltaPercent()).isEqualByComparingTo("-85.00");
        assertThat(result.summary().status()).isEqualTo(CycleComparisonStatus.BELOW_BASELINE);

        CycleComparisonCategoryDto groceries = result.categories().getFirst();
        assertThat(groceries.categoryId()).isEqualTo(1);
        assertThat(groceries.currentAmount()).isEqualByComparingTo("30.00");
        assertThat(groceries.baselineAverageAmount()).isEqualByComparingTo("200.00");
        assertThat(groceries.deltaAmount()).isEqualByComparingTo("-170.00");
        assertThat(groceries.deltaPercent()).isEqualByComparingTo("-85.00");

        assertThat(result.series().currentCumulative()).hasSize(17);
        assertThat(result.series().currentCumulative().get(0).amount()).isEqualByComparingTo("10.00");
        assertThat(result.series().currentCumulative().get(16).amount()).isEqualByComparingTo("30.00");
        assertThat(result.series().baselineAverageCumulative()).hasSize(17);
        assertThat(result.series().baselineAverageCumulative().get(16).amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void noBaselineCyclesReturnsUnavailableBaselineAndNoBaselineStatus() {
        givenAnchorDates(LocalDate.of(2026, 5, 1));
        givenRows(List.of(row(LocalDate.of(2026, 5, 1), 1, "Groceries", "G", "42.00", 1L)));

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, AS_OF, 3, null, CategoryAggregationMode.ALL, null);

        assertThat(result.baseline().available()).isFalse();
        assertThat(result.baseline().cyclesUsed()).isZero();
        assertThat(result.summary().currentExpenses()).isEqualByComparingTo("42.00");
        assertThat(result.summary().baselineAverageExpenses()).isEqualByComparingTo("0.00");
        assertThat(result.summary().deltaPercent()).isNull();
        assertThat(result.summary().status()).isEqualTo(CycleComparisonStatus.NO_BASELINE);
        assertThat(result.categories().getFirst().status()).isEqualTo(CycleComparisonStatus.NO_BASELINE);
    }

    @Test
    void noAnchorCategoryFallsBackToCalendarMonthCycles() {
        when(categoryRepository.findByUserIdAndIsCycleAnchorTrue(USER_ID)).thenReturn(Optional.empty());
        givenRows(List.of(
                row(LocalDate.of(2026, 3, 1), 1, "Groceries", "G", "30.00", 1L),
                row(LocalDate.of(2026, 2, 17), 1, "Groceries", "G", "90.00", 1L)));

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, LocalDate.of(2026, 3, 17), 1,
                null, CategoryAggregationMode.ALL, null);

        assertThat(result.currentCycle().startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(result.currentCycle().endDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(result.baseline().cycles().getFirst().startDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(result.baseline().cycles().getFirst().cutoffDate()).isEqualTo(LocalDate.of(2026, 2, 17));
        assertThat(result.summary().currentExpenses()).isEqualByComparingTo("30.00");
        assertThat(result.summary().baselineAverageExpenses()).isEqualByComparingTo("90.00");
    }

    @Test
    void previousShorterCycleClampsCutoffDate() {
        when(categoryRepository.findByUserIdAndIsCycleAnchorTrue(USER_ID)).thenReturn(Optional.empty());
        givenRows(List.of(
                row(LocalDate.of(2026, 3, 31), 1, "Groceries", "G", "31.00", 1L),
                row(LocalDate.of(2026, 2, 28), 1, "Groceries", "G", "28.00", 1L)));

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, LocalDate.of(2026, 3, 31), 1,
                null, CategoryAggregationMode.ALL, null);

        assertThat(result.currentCycle().dayIndex()).isEqualTo(31);
        assertThat(result.baseline().cycles().getFirst().cutoffDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(result.baseline().cycles().getFirst().comparableDays()).isEqualTo(28);
        assertThat(result.baseline().cycles().getFirst().expensesAtCutoff()).isEqualByComparingTo("28.00");
        assertThat(result.series().baselineAverageCumulative()).hasSize(31);
        assertThat(result.series().baselineAverageCumulative().get(30).amount()).isEqualByComparingTo("28.00");
    }

    @Test
    void asOfDateAfterCurrentCycleEndKeepsRequestedDateButUsesCycleEndAsCutoff() {
        LocalDate requestedAsOfDate = LocalDate.of(2026, 6, 10);
        givenAnchorDatesForAsOf(requestedAsOfDate,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 4, 1));
        givenRows(List.of(
                row(LocalDate.of(2026, 5, 31), 1, "Groceries", "G", "50.00", 1L),
                row(LocalDate.of(2026, 6, 1), 1, "Groceries", "G", "999.00", 1L),
                row(LocalDate.of(2026, 4, 30), 1, "Groceries", "G", "25.00", 1L)));

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, requestedAsOfDate, 1, null, CategoryAggregationMode.ALL, null);

        assertThat(result.asOfDate()).isEqualTo(requestedAsOfDate);
        assertThat(result.currentCycle().endDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(result.currentCycle().cutoffDate()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(result.currentCycle().dayIndex()).isEqualTo(31);
        assertThat(result.currentCycle().elapsedDays()).isEqualTo(31);
        assertThat(result.summary().currentExpenses()).isEqualByComparingTo("50.00");

        verify(transactionRepository).findDailyCategorySpendByWalletUserDateRangeAndType(
                eq(WALLET_ID),
                eq(USER_ID),
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 5, 31)),
                eq(CategoryType.EXPENSE),
                anyList(),
                anyBoolean(),
                anyBoolean(),
                anyList(),
                anyBoolean());
    }

    @Test
    void newSpendStatusWhenCurrentAmountExistsAndBaselineIsZero() {
        givenAnchorDates(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1));
        givenRows(List.of(row(LocalDate.of(2026, 5, 10), 7, "Games", "G", "64.00", 1L)));

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, AS_OF, 1, null, CategoryAggregationMode.ALL, null);

        assertThat(result.summary().baselineAverageExpenses()).isEqualByComparingTo("0.00");
        assertThat(result.summary().deltaAmount()).isEqualByComparingTo("64.00");
        assertThat(result.summary().deltaPercent()).isNull();
        assertThat(result.summary().status()).isEqualTo(CycleComparisonStatus.NEW_SPEND);
        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().getFirst().status()).isEqualTo(CycleComparisonStatus.NEW_SPEND);
        assertThat(result.categories().getFirst().deltaPercent()).isNull();
    }

    @Test
    void inLineStatusWhenDeltaPercentIsWithinThreshold() {
        givenAnchorDates(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1));
        givenRows(List.of(
                row(LocalDate.of(2026, 5, 10), 1, "Groceries", "G", "104.00", 1L),
                row(LocalDate.of(2026, 4, 10), 1, "Groceries", "G", "100.00", 1L)));

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, AS_OF, 1, null, CategoryAggregationMode.ALL, null);

        assertThat(result.summary().deltaPercent()).isEqualByComparingTo("4.00");
        assertThat(result.summary().status()).isEqualTo(CycleComparisonStatus.IN_LINE);
        assertThat(result.categories().getFirst().status()).isEqualTo(CycleComparisonStatus.IN_LINE);
    }

    @Test
    void categoryBaselineAverageDividesByCyclesUsedWhenCategoryIsAbsentInSomeBaselineCycles() {
        givenAnchorDates(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 2, 1));
        givenRows(List.of(
                row(LocalDate.of(2026, 5, 10), 5, "Books", "B", "10.00", 1L),
                row(LocalDate.of(2026, 4, 10), 5, "Books", "B", "90.00", 3L)));

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, AS_OF, 3, null, CategoryAggregationMode.ALL, null);

        CycleComparisonCategoryDto books = result.categories().getFirst();
        assertThat(books.baselineAverageAmount()).isEqualByComparingTo("30.00");
        assertThat(books.baselineAverageTransactionCount()).isEqualByComparingTo("1.00");
    }

    @Test
    void categoryPresentOnlyInBaselineIsReturnedWithZeroCurrentAmountAndBelowBaselineStatus() {
        givenAnchorDates(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1));
        givenRows(List.of(row(LocalDate.of(2026, 4, 10), 6, "Restaurants", "R", "92.00", 2L)));

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, AS_OF, 1, null, CategoryAggregationMode.ALL, null);

        assertThat(result.categories()).hasSize(1);
        CycleComparisonCategoryDto restaurants = result.categories().getFirst();
        assertThat(restaurants.currentAmount()).isEqualByComparingTo("0.00");
        assertThat(restaurants.baselineAverageAmount()).isEqualByComparingTo("92.00");
        assertThat(restaurants.deltaAmount()).isEqualByComparingTo("-92.00");
        assertThat(restaurants.deltaPercent()).isEqualByComparingTo("-100.00");
        assertThat(restaurants.status()).isEqualTo(CycleComparisonStatus.BELOW_BASELINE);
    }

    @Test
    void noTransactionsReturnsZeroResponseWithoutCategories() {
        givenAnchorDates(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1));
        givenRows(List.of());

        CycleComparisonResponseDto result = service.getCycleComparison(
                WALLET_ID, USER_ID, AS_OF, 1, null, CategoryAggregationMode.ALL, null);

        assertThat(result.summary().currentExpenses()).isEqualByComparingTo("0.00");
        assertThat(result.summary().baselineAverageExpenses()).isEqualByComparingTo("0.00");
        assertThat(result.summary().deltaAmount()).isEqualByComparingTo("0.00");
        assertThat(result.summary().deltaPercent()).isNull();
        assertThat(result.summary().status()).isEqualTo(CycleComparisonStatus.IN_LINE);
        assertThat(result.categories()).isEmpty();
        assertThat(result.series().currentCumulative()).hasSize(17);
        assertThat(result.series().baselineAverageCumulative()).hasSize(17);
    }

    @Test
    void categoryIdsCategoryModeImportanceAndExpenseTypeArePassedToRepository() {
        givenAnchorDates(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1));
        givenRows(List.of());

        service.getCycleComparison(
                WALLET_ID,
                USER_ID,
                AS_OF,
                1,
                List.of(10, 11),
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES,
                List.of(Importance.SHOULDNT_HAVE));

        verify(transactionRepository).findDailyCategorySpendByWalletUserDateRangeAndType(
                eq(WALLET_ID),
                eq(USER_ID),
                eq(LocalDate.of(2026, 4, 1)),
                eq(AS_OF),
                eq(CategoryType.EXPENSE),
                eq(List.of(10, 11)),
                eq(false),
                eq(true),
                eq(List.of(Importance.SHOULDNT_HAVE)),
                eq(false));
    }

    @Test
    void invalidBaselineCyclesIsRejected() {
        assertThatThrownBy(() -> service.getCycleComparison(
                WALLET_ID, USER_ID, AS_OF, 13, null, CategoryAggregationMode.ALL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baselineCycles must be between 1 and 12");
    }

    @Test
    void asOfDateBeforeCurrentCycleStartIsRejected() {
        LocalDate requestedAsOfDate = LocalDate.of(2026, 4, 25);
        when(categoryRepository.findByUserIdAndIsCycleAnchorTrue(USER_ID))
                .thenReturn(Optional.of(Category.builder().id(ANCHOR_CATEGORY_ID).build()));
        when(transactionRepository.findByWalletUserCategoryAndDateLessThanEqualOrderByTransactionDateDesc(
                eq(WALLET_ID), eq(USER_ID), eq(ANCHOR_CATEGORY_ID), eq(requestedAsOfDate), any(PageRequest.class)))
                .thenReturn(StreamFactory.transactions(LocalDate.of(2026, 5, 1)));

        assertThatThrownBy(() -> service.getCycleComparison(
                WALLET_ID, USER_ID, requestedAsOfDate, 1, null, CategoryAggregationMode.ALL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("asOfDate must not be before the current cycle start date");
    }

    private void givenAnchorDates(LocalDate... dates) {
        givenAnchorDatesForAsOf(AS_OF, dates);
    }

    private void givenAnchorDatesForAsOf(LocalDate asOfDate, LocalDate... dates) {
        when(categoryRepository.findByUserIdAndIsCycleAnchorTrue(USER_ID))
                .thenReturn(Optional.of(Category.builder().id(ANCHOR_CATEGORY_ID).build()));
        when(transactionRepository.findByWalletUserCategoryAndDateLessThanEqualOrderByTransactionDateDesc(
                eq(WALLET_ID), eq(USER_ID), eq(ANCHOR_CATEGORY_ID), eq(asOfDate), any(PageRequest.class)))
                .thenReturn(StreamFactory.transactions(dates));
    }

    private void givenRows(List<DailyCategorySpendProjection> rows) {
        when(transactionRepository.findDailyCategorySpendByWalletUserDateRangeAndType(
                eq(WALLET_ID),
                eq(USER_ID),
                any(LocalDate.class),
                any(LocalDate.class),
                eq(CategoryType.EXPENSE),
                anyList(),
                anyBoolean(),
                anyBoolean(),
                anyList(),
                anyBoolean()))
                .thenReturn(rows);
    }

    private DailyCategorySpendProjection row(
            LocalDate date, Integer categoryId, String name, String emoji, String amount, Long count) {
        return new DailyCategorySpendProjection() {
            @Override public LocalDate getDate() { return date; }
            @Override public Integer getCategoryId() { return categoryId; }
            @Override public String getName() { return name; }
            @Override public String getEmoji() { return emoji; }
            @Override public BigDecimal getAmount() { return new BigDecimal(amount); }
            @Override public Long getTransactionCount() { return count; }
        };
    }

    private static class StreamFactory {
        static List<Transaction> transactions(LocalDate... dates) {
            return java.util.Arrays.stream(dates)
                    .map(date -> Transaction.builder().transactionDate(date).build())
                    .toList();
        }
    }
}
