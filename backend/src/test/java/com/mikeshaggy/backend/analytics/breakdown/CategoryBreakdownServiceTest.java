package com.mikeshaggy.backend.analytics.breakdown;

import com.mikeshaggy.backend.analytics.breakdown.CategoryBreakdownDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.transaction.repository.CategoryBreakdownProjection;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryBreakdownServiceTest {

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

    private CategoryBreakdownService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-19T10:00:00Z"), ZoneOffset.UTC);
        service = new CategoryBreakdownService(new com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService(transactionRepository), walletService, periodService, clock);
    }

    @Test
    void emptyPeriodReturnsZeroAndEmptyCategories() {
        resolvedPeriod();
        when(transactionRepository.findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE)).thenReturn(List.of());

        CategoryBreakdownDto result = service.getCategoryBreakdown(WALLET_ID, USER_ID, PeriodType.CUSTOM, START, END);

        assertThat(result.totalExpenses()).isEqualByComparingTo("0.00");
        assertThat(result.categories()).isEmpty();
    }

    @Test
    void singleExpenseCategoryReturnsFullShare() {
        resolvedPeriod();
        when(transactionRepository.findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(row(12, "Groceries", "🛒", "820.00", 14L)));

        CategoryBreakdownDto result = service.getCategoryBreakdown(WALLET_ID, USER_ID, PeriodType.CUSTOM, START, END);

        assertThat(result.totalExpenses()).isEqualByComparingTo("820.00");
        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().getFirst().categoryId()).isEqualTo(12);
        assertThat(result.categories().getFirst().amount()).isEqualByComparingTo("820.00");
        assertThat(result.categories().getFirst().share()).isEqualByComparingTo("100.00");
        assertThat(result.categories().getFirst().transactionCount()).isEqualTo(14L);
    }

    @Test
    void multipleExpenseCategoriesKeepAmountOrderAndCalculateShares() {
        resolvedPeriod();
        when(transactionRepository.findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        row(12, "Groceries", "🛒", "820.00", 14L),
                        row(5, "Transport", "🚗", "410.00", 8L),
                        row(9, "Coffee", "☕", "50.00", 2L)));

        CategoryBreakdownDto result = service.getCategoryBreakdown(WALLET_ID, USER_ID, PeriodType.CUSTOM, START, END);

        assertThat(result.totalExpenses()).isEqualByComparingTo("1280.00");
        assertThat(result.categories()).extracting("name")
                .containsExactly("Groceries", "Transport", "Coffee");
        assertThat(result.categories()).extracting("share")
                .containsExactly(
                        new BigDecimal("64.06"),
                        new BigDecimal("32.03"),
                        new BigDecimal("3.91"));
    }

    @Test
    void incomeCategoriesAreExcludedByExplicitExpenseTypeFilter() {
        resolvedPeriod();
        when(transactionRepository.findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(row(12, "Groceries", "🛒", "100.00", 1L)));

        service.getCategoryBreakdown(WALLET_ID, USER_ID, PeriodType.CUSTOM, START, END);

        verify(transactionRepository).findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE);
    }

    @Test
    void usesFullCategoryAggregationSoExcludedTopCategoriesStillAppearInBreakdown() {
        resolvedPeriod();
        when(transactionRepository.findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        row(1, "Rent", "🏠", "1200.00", 1L),
                        row(2, "Groceries", "🛒", "300.00", 3L)));

        CategoryBreakdownDto result = service.getCategoryBreakdown(WALLET_ID, USER_ID,
                PeriodType.CUSTOM, START, END);

        assertThat(result.totalExpenses()).isEqualByComparingTo("1500.00");
        assertThat(result.categories()).extracting(CategoryBreakdownItemDto::name)
                .containsExactly("Rent", "Groceries");
    }

    @Test
    void dateRangeFilteringUsesResolvedDates() {
        resolvedPeriod();
        when(transactionRepository.findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE)).thenReturn(List.of());

        service.getCategoryBreakdown(WALLET_ID, USER_ID, PeriodType.CUSTOM, START, END);

        verify(periodService).resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, START, END);
        verify(transactionRepository).findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE);
    }

    @Test
    void walletOwnershipIsCheckedBeforeQueryingBreakdown() {
        resolvedPeriod();
        when(transactionRepository.findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE)).thenReturn(List.of());

        service.getCategoryBreakdown(WALLET_ID, USER_ID, PeriodType.CUSTOM, START, END);

        verify(walletService).getWalletEntityByIdForUser(WALLET_ID, USER_ID);
    }

    private void resolvedPeriod() {
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, START, END))
                .thenReturn(new PeriodDto(START, END, END.plusDays(1), PeriodType.CUSTOM));
    }

    private CategoryBreakdownProjection row(Integer categoryId, String name, String emoji,
                                            String amount, Long transactionCount) {
        return new CategoryBreakdownProjection() {
            @Override
            public Integer getCategoryId() {
                return categoryId;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getEmoji() {
                return emoji;
            }

            @Override
            public BigDecimal getAmount() {
                return new BigDecimal(amount);
            }

            @Override
            public Long getTransactionCount() {
                return transactionCount;
            }
        };
    }
}
