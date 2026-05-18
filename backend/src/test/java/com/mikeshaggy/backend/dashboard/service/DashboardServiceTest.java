package com.mikeshaggy.backend.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.*;
import com.mikeshaggy.backend.dashboard.service.calculator.SummaryCalculator;
import com.mikeshaggy.backend.dashboard.service.calculator.TopCategoriesCalculator;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private PeriodService periodService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private SummaryCalculator summaryCalculator;

    @Mock
    private TopCategoriesCalculator topCategoriesCalculator;

    @Mock
    private WalletService walletService;

    @Mock
    private FixedPaymentDashboardService fixedPaymentDashboardService;

    private DashboardService dashboardService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Integer WALLET_ID = 1;

    @BeforeEach
    void setUp() {
        dashboardService =
                new DashboardService(
                        periodService,
                        transactionService,
                        summaryCalculator,
                        topCategoriesCalculator,
                        walletService,
                        fixedPaymentDashboardService);
    }

    private Category category(String name, CategoryType type) {
        return Category.builder().id(1).name(name).type(type).build();
    }

    private Transaction transaction(BigDecimal amount, String categoryName, CategoryType type) {
        return Transaction.builder()
                .id(1L)
                .amount(amount)
                .category(category(categoryName, type))
                .transactionDate(LocalDate.of(2026, 3, 5))
                .build();
    }

    private SummaryDto summaryDto(String income, String expenses, String saved) {
        return new SummaryDto(
                new BigDecimal(income),
                new BigDecimal(expenses),
                new BigDecimal(saved),
                new BigDecimal("42.86"),
                new PercentageChangeDto(BigDecimal.ZERO, true),
                new PercentageChangeDto(BigDecimal.ZERO, true),
                new PercentageChangeDto(BigDecimal.ZERO, true));
    }

    @Nested
    class PayCycleHappyPath {

        @Test
        void assemblesCompleteDashboardFromAllSubServices() {
            // given
            PeriodDto currentPeriod =
                    new PeriodDto(
                            LocalDate.of(2026, 2, 25),
                            LocalDate.of(2026, 3, 11),
                            LocalDate.of(2026, 3, 25),
                            PeriodType.PAY_CYCLE);
            PeriodDto comparePeriod =
                    new PeriodDto(
                            LocalDate.of(2026, 1, 25),
                            LocalDate.of(2026, 2, 24),
                            LocalDate.of(2026, 2, 25),
                            PeriodType.LAST_PAY_CYCLE);

            List<Transaction> currentTxns =
                    List.of(
                            transaction(new BigDecimal("3500.00"), "Salary", CategoryType.INCOME),
                            transaction(new BigDecimal("1200.00"), "Groceries", CategoryType.EXPENSE),
                            transaction(new BigDecimal("800.00"), "Transport", CategoryType.EXPENSE));
            List<Transaction> compareTxns =
                    List.of(
                            transaction(new BigDecimal("3500.00"), "Salary", CategoryType.INCOME),
                            transaction(new BigDecimal("1000.00"), "Groceries", CategoryType.EXPENSE));

            SummaryDto summary = summaryDto("3500.00", "2000.00", "1500.00");
            List<CategorySpendingDto> topCategories =
                    List.of(
                            new CategorySpendingDto(
                                    "Groceries",
                                    new BigDecimal("1200.00"),
                                    new BigDecimal("60.00"),
                                    new PercentageChangeDto(new BigDecimal("20.00"), false)),
                            new CategorySpendingDto(
                                    "Transport",
                                    new BigDecimal("800.00"),
                                    new BigDecimal("40.00"),
                                    new PercentageChangeDto(BigDecimal.ZERO, true)));
            FixedTransactionsTileDto fixedTile = mock(FixedTransactionsTileDto.class);

            when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                    .thenReturn(new ResolvedPeriods(currentPeriod, comparePeriod));
            when(transactionService.getTransactionsForWalletAndPeriod(WALLET_ID, currentPeriod))
                    .thenReturn(currentTxns);
            when(transactionService.getTransactionsForWalletAndPeriod(WALLET_ID, comparePeriod))
                    .thenReturn(compareTxns);
            when(summaryCalculator.calculate(currentTxns, compareTxns)).thenReturn(summary);
            when(topCategoriesCalculator.calculate(currentTxns, compareTxns)).thenReturn(topCategories);
            when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                    .thenReturn(Wallet.builder().id(WALLET_ID).name("Main Wallet").build());
            when(fixedPaymentDashboardService.getFixedPaymentsTileData(currentPeriod, WALLET_ID, USER_ID))
                    .thenReturn(fixedTile);

            // when
            DashboardData result =
                    dashboardService.getDashboardData(USER_ID, WALLET_ID, null, null, PeriodType.PAY_CYCLE);

            // then
            assertThat(result.period()).isEqualTo(currentPeriod);
            assertThat(result.summary()).isEqualTo(summary);
            assertThat(result.topCategories()).hasSize(2);
            assertThat(result.topCategories().get(0).categoryName()).isEqualTo("Groceries");
            assertThat(result.walletName()).isEqualTo("Main Wallet");
            assertThat(result.fixedPaymentsTile()).isSameAs(fixedTile);
        }
    }

    @Nested
    class CustomPeriod {

        @Test
        void passesCustomDatesToResolverAndAssemblesDashboard() {
            // given
            PeriodDto customPeriod =
                    new PeriodDto(
                            LocalDate.of(2026, 1, 1),
                            LocalDate.of(2026, 1, 31),
                            LocalDate.of(2026, 2, 1),
                            PeriodType.CUSTOM);
            PeriodDto comparePeriod =
                    new PeriodDto(
                            LocalDate.of(2025, 12, 1),
                            LocalDate.of(2025, 12, 31),
                            LocalDate.of(2026, 1, 1),
                            PeriodType.CUSTOM);

            when(periodService.resolvePeriods(
                            PeriodType.CUSTOM,
                            WALLET_ID,
                            USER_ID,
                            LocalDate.of(2026, 1, 1),
                            LocalDate.of(2026, 1, 31)))
                    .thenReturn(new ResolvedPeriods(customPeriod, comparePeriod));
            when(transactionService.getTransactionsForWalletAndPeriod(eq(WALLET_ID), any()))
                    .thenReturn(Collections.emptyList());
            when(summaryCalculator.calculate(anyList(), anyList()))
                    .thenReturn(summaryDto("0.00", "0.00", "0.00"));
            when(topCategoriesCalculator.calculate(anyList(), anyList()))
                    .thenReturn(Collections.emptyList());
            when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                    .thenReturn(Wallet.builder().id(WALLET_ID).name("Main Wallet").build());
            when(fixedPaymentDashboardService.getFixedPaymentsTileData(customPeriod, WALLET_ID, USER_ID))
                    .thenReturn(mock(FixedTransactionsTileDto.class));

            // when
            DashboardData result =
                    dashboardService.getDashboardData(
                            USER_ID,
                            WALLET_ID,
                            LocalDate.of(2026, 1, 1),
                            LocalDate.of(2026, 1, 31),
                            PeriodType.CUSTOM);

            // then
            assertThat(result.period()).isEqualTo(customPeriod);
            assertThat(result.period().periodType()).isEqualTo(PeriodType.CUSTOM);

            verify(periodService)
                    .resolvePeriods(
                            PeriodType.CUSTOM,
                            WALLET_ID,
                            USER_ID,
                            LocalDate.of(2026, 1, 1),
                            LocalDate.of(2026, 1, 31));
        }
    }

    @Nested
    class NullComparePeriod {

        @Test
        void usesEmptyListWhenComparePeriodIsNull() {
            // given
            PeriodDto currentPeriod =
                    new PeriodDto(
                            LocalDate.of(2026, 3, 1),
                            LocalDate.of(2026, 3, 11),
                            LocalDate.of(2026, 4, 1),
                            PeriodType.PAY_CYCLE);

            List<Transaction> currentTxns =
                    List.of(transaction(new BigDecimal("3000.00"), "Salary", CategoryType.INCOME));

            when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                    .thenReturn(new ResolvedPeriods(currentPeriod, null));
            when(transactionService.getTransactionsForWalletAndPeriod(WALLET_ID, currentPeriod))
                    .thenReturn(currentTxns);
            when(summaryCalculator.calculate(eq(currentTxns), eq(Collections.emptyList())))
                    .thenReturn(summaryDto("3000.00", "0.00", "3000.00"));
            when(topCategoriesCalculator.calculate(eq(currentTxns), eq(Collections.emptyList())))
                    .thenReturn(Collections.emptyList());
            when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                    .thenReturn(Wallet.builder().id(WALLET_ID).name("Main Wallet").build());
            when(fixedPaymentDashboardService.getFixedPaymentsTileData(currentPeriod, WALLET_ID, USER_ID))
                    .thenReturn(mock(FixedTransactionsTileDto.class));

            // when
            DashboardData result =
                    dashboardService.getDashboardData(USER_ID, WALLET_ID, null, null, PeriodType.PAY_CYCLE);

            // then
            assertThat(result.summary().income()).isEqualByComparingTo("3000.00");

            verify(transactionService, times(1)).getTransactionsForWalletAndPeriod(anyInt(), any());
            verify(summaryCalculator).calculate(eq(currentTxns), eq(Collections.emptyList()));
            verify(topCategoriesCalculator).calculate(eq(currentTxns), eq(Collections.emptyList()));
        }
    }

    @Nested
    class WalletScoping {

        @Test
        void walletIdIsPassedThroughToAllDependencies() {
            // given
            Integer specificWalletId = 42;
            PeriodDto period =
                    new PeriodDto(
                            LocalDate.of(2026, 3, 1),
                            LocalDate.of(2026, 3, 11),
                            LocalDate.of(2026, 4, 1),
                            PeriodType.PAY_CYCLE);

            when(periodService.resolvePeriods(
                            PeriodType.PAY_CYCLE, specificWalletId, USER_ID, null, null))
                    .thenReturn(new ResolvedPeriods(period, null));
            when(transactionService.getTransactionsForWalletAndPeriod(eq(specificWalletId), any()))
                    .thenReturn(Collections.emptyList());
            when(summaryCalculator.calculate(anyList(), anyList()))
                    .thenReturn(summaryDto("0.00", "0.00", "0.00"));
            when(topCategoriesCalculator.calculate(anyList(), anyList()))
                    .thenReturn(Collections.emptyList());
            when(walletService.getWalletEntityByIdForUser(specificWalletId, USER_ID))
                    .thenReturn(Wallet.builder().id(specificWalletId).name("Savings").build());
            when(fixedPaymentDashboardService.getFixedPaymentsTileData(period, specificWalletId, USER_ID))
                    .thenReturn(mock(FixedTransactionsTileDto.class));

            // when
            DashboardData result =
                    dashboardService.getDashboardData(
                            USER_ID, specificWalletId, null, null, PeriodType.PAY_CYCLE);

            // then
            assertThat(result.walletName()).isEqualTo("Savings");

            verify(periodService)
                    .resolvePeriods(PeriodType.PAY_CYCLE, specificWalletId, USER_ID, null, null);
            verify(transactionService).getTransactionsForWalletAndPeriod(eq(specificWalletId), any());
            verify(walletService).getWalletEntityByIdForUser(specificWalletId, USER_ID);
            verify(fixedPaymentDashboardService)
                    .getFixedPaymentsTileData(period, specificWalletId, USER_ID);
        }
    }

    @Nested
    class UnsupportedPeriodType {

        @Test
        void throwsForUnregisteredPeriodType() {
            // given
            when(periodService.resolvePeriods(
                            eq(PeriodType.LAST_PAY_CYCLE), anyInt(), any(), any(), any()))
                    .thenThrow(new IllegalArgumentException("Unsupported period type: LAST_PAY_CYCLE"));

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    dashboardService.getDashboardData(
                                            USER_ID, WALLET_ID, null, null, PeriodType.LAST_PAY_CYCLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported period type");
        }
    }
}
