package com.mikeshaggy.backend.analytics.query;

import com.mikeshaggy.backend.category.domain.CategoryType;
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
