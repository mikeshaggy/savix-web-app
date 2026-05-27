package com.mikeshaggy.backend.analytics.breakdown;

import com.mikeshaggy.backend.analytics.breakdown.ImportanceBreakdownDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.repository.ImportanceBreakdownProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportanceBreakdownServiceTest {

    private static final Integer WALLET_ID = 1;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate START = LocalDate.of(2026, 3, 1);
    private static final LocalDate END = LocalDate.of(2026, 3, 31);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private PeriodService periodService;

    private ImportanceBreakdownService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-19T10:00:00Z"), ZoneOffset.UTC);
        service = new ImportanceBreakdownService(transactionRepository, walletService, periodService, clock);
    }

    @Test
    void emptyPeriodReturnsZeroAndEmptyBreakdown() {
        resolvedPeriod();
        when(transactionRepository.findImportanceBreakdownByWalletDateRangeAndType(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE)).thenReturn(List.of());

        ImportanceBreakdownDto result = service.getImportanceBreakdown(WALLET_ID, USER_ID,
                PeriodType.CUSTOM, START, END);

        assertThat(result.totalExpenses()).isEqualByComparingTo("0.00");
        assertThat(result.breakdown()).isEmpty();
    }

    @Test
    void incomeWithNullImportanceIsExcludedFromImportanceBreakdown() {
        resolvedPeriod();
        when(transactionRepository.findImportanceBreakdownByWalletDateRangeAndType(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(row(Importance.ESSENTIAL, "75.00", 2L)));

        ImportanceBreakdownDto result = service.getImportanceBreakdown(WALLET_ID, USER_ID,
                PeriodType.CUSTOM, START, END);

        assertThat(result.totalExpenses()).isEqualByComparingTo("75.00");
        assertThat(result.breakdown()).hasSize(1);
        assertThat(result.breakdown().getFirst().importance()).isEqualTo(Importance.ESSENTIAL);
        assertThat(result.breakdown().getFirst().amount()).isEqualByComparingTo("75.00");
        assertThat(result.breakdown().getFirst().share()).isEqualByComparingTo("100.00");
        assertThat(result.breakdown()).noneMatch(item -> item.importance() == null);
    }

    @Test
    void invalidExpenseWithNullImportanceIsIgnoredDefensively() {
        resolvedPeriod();
        when(transactionRepository.findImportanceBreakdownByWalletDateRangeAndType(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        row(null, "25.00", 1L),
                        row(Importance.ESSENTIAL, "75.00", 2L)));

        ImportanceBreakdownDto result = service.getImportanceBreakdown(WALLET_ID, USER_ID,
                PeriodType.CUSTOM, START, END);

        assertThat(result.totalExpenses()).isEqualByComparingTo("75.00");
        assertThat(result.breakdown()).hasSize(1);
        assertThat(result.breakdown().getFirst().importance()).isEqualTo(Importance.ESSENTIAL);
        assertThat(result.breakdown().getFirst().share()).isEqualByComparingTo("100.00");
        assertThat(result.breakdown()).noneMatch(item -> item.importance() == null);
    }

    @Test
    void allExpenseRowsHaveImportanceAndSharesSumTo100() {
        resolvedPeriod();
        when(transactionRepository.findImportanceBreakdownByWalletDateRangeAndType(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        row(Importance.NICE_TO_HAVE, "600.00", 18L),
                        row(Importance.ESSENTIAL, "1200.00", 5L),
                        row(Importance.HAVE_TO_HAVE, "800.00", 12L),
                        row(Importance.SHOULDNT_HAVE, "400.00", 8L),
                        row(Importance.INVESTMENT, "200.00", 4L)));

        ImportanceBreakdownDto result = service.getImportanceBreakdown(WALLET_ID, USER_ID,
                PeriodType.CUSTOM, START, END);

        assertThat(result.totalExpenses()).isEqualByComparingTo("3200.00");
        assertThat(result.breakdown()).extracting("importance").containsExactly(
                Importance.ESSENTIAL,
                Importance.HAVE_TO_HAVE,
                Importance.NICE_TO_HAVE,
                Importance.SHOULDNT_HAVE,
                Importance.INVESTMENT);
        assertThat(result.breakdown()).extracting("share").containsExactly(
                new BigDecimal("37.50"),
                new BigDecimal("25.00"),
                new BigDecimal("18.75"),
                new BigDecimal("12.50"),
                new BigDecimal("6.25"));
        assertThat(result.breakdown().getFirst().count()).isEqualTo(5L);
    }

    @Test
    void expenseBreakdownUsesOnlyExpenseTransactions() {
        resolvedPeriod();
        when(transactionRepository.findImportanceBreakdownByWalletDateRangeAndType(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE)).thenReturn(List.of());

        service.getImportanceBreakdown(WALLET_ID, USER_ID, PeriodType.CUSTOM, START, END);

        verify(transactionRepository).findImportanceBreakdownByWalletDateRangeAndType(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE);
    }

    @Test
    void orderIsStableEvenWhenRepositoryReturnsDifferentOrder() {
        resolvedPeriod();
        when(transactionRepository.findImportanceBreakdownByWalletDateRangeAndType(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        row(Importance.INVESTMENT, "10.00", 1L),
                        row(Importance.NICE_TO_HAVE, "10.00", 1L),
                        row(Importance.ESSENTIAL, "10.00", 1L)));

        ImportanceBreakdownDto result = service.getImportanceBreakdown(WALLET_ID, USER_ID,
                PeriodType.CUSTOM, START, END);

        assertThat(result.breakdown()).extracting("importance").containsExactly(
                Importance.ESSENTIAL,
                Importance.NICE_TO_HAVE,
                Importance.INVESTMENT);
    }

    @Test
    void dateRangeFilteringUsesResolvedDates() {
        resolvedPeriod();
        when(transactionRepository.findImportanceBreakdownByWalletDateRangeAndType(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE)).thenReturn(List.of());

        service.getImportanceBreakdown(WALLET_ID, USER_ID, PeriodType.CUSTOM, START, END);

        verify(periodService).resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, START, END);
        verify(transactionRepository).findImportanceBreakdownByWalletDateRangeAndType(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE);
    }

    private void resolvedPeriod() {
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, START, END))
                .thenReturn(new PeriodDto(START, END, END.plusDays(1), PeriodType.CUSTOM));
    }

    private ImportanceBreakdownProjection row(Importance importance, String amount, Long count) {
        return new ImportanceBreakdownProjection() {
            @Override
            public Importance getImportance() {
                return importance;
            }

            @Override
            public BigDecimal getAmount() {
                return new BigDecimal(amount);
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }
}
