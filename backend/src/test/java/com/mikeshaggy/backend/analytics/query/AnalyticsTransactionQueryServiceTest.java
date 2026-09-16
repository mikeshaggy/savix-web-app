package com.mikeshaggy.backend.analytics.query;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.DailyTotalProjection;
import com.mikeshaggy.backend.transaction.repository.HeatmapProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsTransactionQueryServiceTest {

    private static final Integer WALLET_ID = 1;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private TransactionRepository transactionRepository;

    @Test
    void dailyExpenseStatsAggregatesHeatmapRowsByDate() {
        AnalyticsTransactionQueryService service = new AnalyticsTransactionQueryService(transactionRepository);
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);
        when(transactionRepository.findHeatmapByWalletDateRangeAndType(
                WALLET_ID, USER_ID, from, to, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        heatmapRow(LocalDate.of(2026, 5, 3), "40.00"),
                        heatmapRow(LocalDate.of(2026, 5, 3), "25.00"),
                        heatmapRow(LocalDate.of(2026, 5, 4), "50.00")));

        AnalyticsTransactionQueryService.DailyExpenseStats stats =
                service.dailyExpenseStats(WALLET_ID, USER_ID, from, to);

        assertThat(stats.highestSpendingDay()).isEqualTo(LocalDate.of(2026, 5, 3));
        assertThat(stats.highestSpendingDayAmount()).isEqualByComparingTo("65.00");
        assertThat(stats.activeDays()).isEqualTo(2);
    }

    @Test
    void dailyExpenseStatsReturnsEmptyValuesWhenNoRowsExist() {
        AnalyticsTransactionQueryService service = new AnalyticsTransactionQueryService(transactionRepository);
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);
        when(transactionRepository.findHeatmapByWalletDateRangeAndType(
                WALLET_ID, USER_ID, from, to, CategoryType.EXPENSE))
                .thenReturn(List.of());

        AnalyticsTransactionQueryService.DailyExpenseStats stats =
                service.dailyExpenseStats(WALLET_ID, USER_ID, from, to);

        assertThat(stats.highestSpendingDay()).isNull();
        assertThat(stats.highestSpendingDayAmount()).isNull();
        assertThat(stats.activeDays()).isZero();
    }

    @Test
    void sumUnlinkedForPaceDelegatesToPaceEligibleQueryAndNormalisesScale() {
        AnalyticsTransactionQueryService service = new AnalyticsTransactionQueryService(transactionRepository);
        LocalDate from = LocalDate.of(2026, 9, 9);
        LocalDate to = LocalDate.of(2026, 9, 14);
        when(transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRange(WALLET_ID, USER_ID, from, to))
                .thenReturn(new BigDecimal("1552.0"));

        assertThat(service.sumUnlinkedForPace(WALLET_ID, USER_ID, from, to)).isEqualTo(new BigDecimal("1552.00"));
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void dailyVariableTotalsZeroFillsEveryDayInRangeInclusive() {
        AnalyticsTransactionQueryService service = new AnalyticsTransactionQueryService(transactionRepository);
        LocalDate from = LocalDate.of(2026, 9, 9);
        LocalDate to = LocalDate.of(2026, 9, 14);
        when(transactionRepository.findDailyPaceEligibleVariableTotals(WALLET_ID, USER_ID, from, to))
                .thenReturn(List.of(
                        dailyRow(LocalDate.of(2026, 9, 9), "94.98"),
                        dailyRow(LocalDate.of(2026, 9, 10), "335.64"),
                        dailyRow(LocalDate.of(2026, 9, 13), "443")));

        List<AnalyticsTransactionQueryService.DailyTotal> totals =
                service.dailyVariableTotals(WALLET_ID, USER_ID, from, to);

        assertThat(totals).extracting(AnalyticsTransactionQueryService.DailyTotal::day).containsExactly(
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 14));
        assertThat(totals).extracting(AnalyticsTransactionQueryService.DailyTotal::amount).containsExactly(
                new BigDecimal("94.98"), new BigDecimal("335.64"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("443.00"), new BigDecimal("0.00"));
    }

    @Test
    void dailyVariableTotalsSingleDayRangeAndEmptyRepositoryYieldZeroRow() {
        AnalyticsTransactionQueryService service = new AnalyticsTransactionQueryService(transactionRepository);
        LocalDate day = LocalDate.of(2026, 9, 9);
        when(transactionRepository.findDailyPaceEligibleVariableTotals(WALLET_ID, USER_ID, day, day))
                .thenReturn(List.of());

        assertThat(service.dailyVariableTotals(WALLET_ID, USER_ID, day, day))
                .containsExactly(new AnalyticsTransactionQueryService.DailyTotal(day, new BigDecimal("0.00")));
    }

    @Test
    void unlinkedExpensesMapsRowsAndFoldsBothExclusionFlagsIntoOne() {
        AnalyticsTransactionQueryService service = new AnalyticsTransactionQueryService(transactionRepository);
        LocalDate from = LocalDate.of(2026, 9, 9);
        LocalDate to = LocalDate.of(2026, 9, 14);
        Category groceries = Category.builder().name("Groceries").type(CategoryType.EXPENSE).build();
        Category moneyLent = Category.builder().name("Money lent").type(CategoryType.EXPENSE).excludedFromPace(true).build();
        when(transactionRepository.findUnlinkedExpensesByWalletUserDateRange(WALLET_ID, USER_ID, from, to))
                .thenReturn(List.of(
                        Transaction.builder().id(1L).title("Bread").category(groceries).amount(new BigDecimal("5.5"))
                                .transactionDate(from).build(),
                        Transaction.builder().id(2L).title("Lent").category(moneyLent).amount(new BigDecimal("150"))
                                .transactionDate(from.plusDays(1)).build(),
                        Transaction.builder().id(3L).title("Repayment").category(groceries).amount(new BigDecimal("300"))
                                .transactionDate(from.plusDays(2)).excludedFromPace(true).build()));

        List<AnalyticsTransactionQueryService.VariableExpense> rows = service.unlinkedExpenses(WALLET_ID, USER_ID, from, to);

        assertThat(rows).containsExactly(
                new AnalyticsTransactionQueryService.VariableExpense(1L, from, "Bread", "Groceries", new BigDecimal("5.50"), false),
                new AnalyticsTransactionQueryService.VariableExpense(2L, from.plusDays(1), "Lent", "Money lent", new BigDecimal("150.00"), true),
                new AnalyticsTransactionQueryService.VariableExpense(3L, from.plusDays(2), "Repayment", "Groceries", new BigDecimal("300.00"), true));
        verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void dailyVariableTotalsIsEmptyWhenFromIsAfterTo() {
        AnalyticsTransactionQueryService service = new AnalyticsTransactionQueryService(transactionRepository);
        LocalDate from = LocalDate.of(2026, 9, 10);
        LocalDate to = LocalDate.of(2026, 9, 9);
        when(transactionRepository.findDailyPaceEligibleVariableTotals(WALLET_ID, USER_ID, from, to))
                .thenReturn(List.of());

        assertThat(service.dailyVariableTotals(WALLET_ID, USER_ID, from, to)).isEmpty();
    }

    private DailyTotalProjection dailyRow(LocalDate day, String amount) {
        return new DailyTotalProjection() {
            @Override public LocalDate getDay() { return day; }
            @Override public BigDecimal getAmount() { return new BigDecimal(amount); }
        };
    }

    private HeatmapProjection heatmapRow(LocalDate date, String amount) {
        return new HeatmapProjection() {
            @Override public LocalDate getDate() { return date; }
            @Override public Integer getCategoryId() { return 1; }
            @Override public String getCategoryName() { return "Groceries"; }
            @Override public String getEmoji() { return null; }
            @Override public BigDecimal getAmount() { return new BigDecimal(amount); }
            @Override public Long getTransactions() { return 1L; }
        };
    }
}
