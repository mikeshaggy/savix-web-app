package com.mikeshaggy.backend.analytics.daily;

import com.mikeshaggy.backend.analytics.daily.HeatmapResponseDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.transaction.repository.HeatmapProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeatmapServiceTest {

    private static final Integer WALLET_ID = 1;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private PeriodService periodService;

    private HeatmapService service;

    @BeforeEach
    void setUp() {
        service = new HeatmapService(transactionRepository, walletService, periodService);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private PeriodDto period(LocalDate from, LocalDate to) {
        return new PeriodDto(from, to, to, PeriodType.CUSTOM);
    }

    private void stubPeriod(LocalDate from, LocalDate to) {
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, from, to))
                .thenReturn(period(from, to));
    }

    private HeatmapResponseDto runHeatmap(LocalDate from, LocalDate to) {
        stubPeriod(from, to);
        return service.getHeatmap(WALLET_ID, USER_ID, PeriodType.CUSTOM, from, to);
    }

    // ─── tests ────────────────────────────────────────────────────────────────────

    @Test
    void periodWithNoTransactionsReturnsAllDaysAndZeroTotals() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(transactionRepository.findHeatmapByWalletDateRangeAndType(
                WALLET_ID, USER_ID, from, to, CategoryType.EXPENSE)).thenReturn(List.of());

        HeatmapResponseDto result = runHeatmap(from, to);

        assertThat(result.startDate()).isEqualTo(from);
        assertThat(result.endDate()).isEqualTo(to);
        assertThat(result.days()).hasSize(31);
        assertThat(result.days().getFirst().date()).isEqualTo(from);
        assertThat(result.days().getLast().date()).isEqualTo(to);
        assertThat(result.days()).allSatisfy(day -> {
            assertThat(day.total()).isEqualByComparingTo("0.00");
            assertThat(day.transactions()).isZero();
            assertThat(day.categories()).isEmpty();
        });
        assertThat(result.maxDayTotal()).isEqualByComparingTo("0.00");
        verify(walletService).getWalletEntityByIdForUser(WALLET_ID, USER_ID);
    }

    @Test
    void multipleTransactionsOnSameDaySumsDailyAndCategoryTotals() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        LocalDate targetDate = LocalDate.of(2026, 3, 12);
        when(transactionRepository.findHeatmapByWalletDateRangeAndType(
                WALLET_ID, USER_ID, from, to, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        row(targetDate, 1, "groceries", "45.50", 2L),
                        row(targetDate, 2, "transport", "10.00", 1L)));

        HeatmapResponseDto result = runHeatmap(from, to);

        var day = result.days().get(11);
        assertThat(day.date()).isEqualTo(targetDate);
        assertThat(day.total()).isEqualByComparingTo("55.50");
        assertThat(day.transactions()).isEqualTo(3L);
        assertThat(day.categories()).hasSize(2);
        assertThat(day.categories().getFirst().categoryId()).isEqualTo(1);
        assertThat(day.categories().getFirst().categoryName()).isEqualTo("groceries");
        assertThat(day.categories().getFirst().amount()).isEqualByComparingTo("45.50");
        assertThat(day.categories().getFirst().transactions()).isEqualTo(2L);
        assertThat(result.maxDayTotal()).isEqualByComparingTo("55.50");
    }

    @Test
    void february2026Returns28Days() {
        LocalDate from = LocalDate.of(2026, 2, 1);
        LocalDate to = LocalDate.of(2026, 2, 28);
        when(transactionRepository.findHeatmapByWalletDateRangeAndType(
                WALLET_ID, USER_ID, from, to, CategoryType.EXPENSE)).thenReturn(List.of());

        HeatmapResponseDto result = runHeatmap(from, to);

        assertThat(result.days()).hasSize(28);
        assertThat(result.days().getLast().date()).isEqualTo(to);
    }

    @Test
    void leapYearFebruaryReturns29Days() {
        LocalDate from = LocalDate.of(2024, 2, 1);
        LocalDate to = LocalDate.of(2024, 2, 29);
        when(transactionRepository.findHeatmapByWalletDateRangeAndType(
                WALLET_ID, USER_ID, from, to, CategoryType.EXPENSE)).thenReturn(List.of());

        HeatmapResponseDto result = runHeatmap(from, to);

        assertThat(result.days()).hasSize(29);
        assertThat(result.days().getLast().date()).isEqualTo(to);
    }

    @Test
    void maxDayTotalReflectsHighestDailyTotalNotHighestSingleCategory() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(transactionRepository.findHeatmapByWalletDateRangeAndType(
                WALLET_ID, USER_ID, from, to, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        row(LocalDate.of(2026, 3, 1), 1, "rent",      "200.00", 1L),
                        row(LocalDate.of(2026, 3, 5), 2, "groceries",  "90.00", 3L),
                        row(LocalDate.of(2026, 3, 5), 3, "transport", "150.00", 2L)));

        HeatmapResponseDto result = runHeatmap(from, to);

        assertThat(result.maxDayTotal()).isEqualByComparingTo("240.00");
        assertThat(result.days().get(0).total()).isEqualByComparingTo("200.00");
        assertThat(result.days().get(4).total()).isEqualByComparingTo("240.00");
        assertThat(result.days().get(4).categories()).hasSize(2);
    }

    @Test
    void arbitraryDateRangeIteratesExactDays() {
        // Pay-cycle style period: mid-March to mid-April (30 days)
        LocalDate from = LocalDate.of(2026, 3, 15);
        LocalDate to = LocalDate.of(2026, 4, 13);
        when(transactionRepository.findHeatmapByWalletDateRangeAndType(
                WALLET_ID, USER_ID, from, to, CategoryType.EXPENSE)).thenReturn(List.of());

        HeatmapResponseDto result = runHeatmap(from, to);

        assertThat(result.days()).hasSize(30);
        assertThat(result.days().getFirst().date()).isEqualTo(from);
        assertThat(result.days().getLast().date()).isEqualTo(to);
    }

    @Test
    void walletNotOwnedByUserDoesNotQueryTransactions() {
        doThrow(new EntityNotFoundException("Wallet not found with id: " + WALLET_ID))
                .when(walletService).getWalletEntityByIdForUser(WALLET_ID, USER_ID);

        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        assertThatThrownBy(() -> service.getHeatmap(WALLET_ID, USER_ID, PeriodType.CUSTOM, from, to))
                .isInstanceOf(EntityNotFoundException.class);

        verifyNoInteractions(transactionRepository);
    }

    private HeatmapProjection row(LocalDate date, Integer categoryId, String categoryName,
                                  String amount, Long transactions) {
        return new HeatmapProjection() {
            @Override public LocalDate getDate()          { return date; }
            @Override public Integer getCategoryId()      { return categoryId; }
            @Override public String getCategoryName()     { return categoryName; }
            @Override public String getEmoji()            { return null; }
            @Override public BigDecimal getAmount()       { return new BigDecimal(amount); }
            @Override public Long getTransactions()       { return transactions; }
        };
    }
}
