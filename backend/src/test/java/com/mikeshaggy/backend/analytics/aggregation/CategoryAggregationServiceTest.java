package com.mikeshaggy.backend.analytics.aggregation;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.repository.CategoryBreakdownProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryAggregationServiceTest {

    private static final Integer WALLET_ID = 1;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate START = LocalDate.of(2026, 3, 1);
    private static final LocalDate END = LocalDate.of(2026, 3, 31);

    @Mock
    private TransactionRepository transactionRepository;

    private CategoryAggregationService service;

    @BeforeEach
    void setUp() {
        service = new CategoryAggregationService(transactionRepository);
    }

    @Test
    void includedModeUsesTopCategoryExclusionQueryAndCalculatesShares() {
        when(transactionRepository.findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        row(1, "Food", "F", "600.00", 3L),
                        row(2, "Transport", "T", "400.00", 2L)));

        CategoryAggregationResult result = service.aggregateExpenses(
                WALLET_ID, USER_ID, START, END, CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.total()).isEqualByComparingTo("1000.00");
        assertThat(result.categories()).extracting(CategoryAggregation::name)
                .containsExactly("Food", "Transport");
        assertThat(result.categories()).extracting(CategoryAggregation::share)
                .containsExactly(new BigDecimal("60.00"), new BigDecimal("40.00"));
        verify(transactionRepository).findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE);
    }

    @Test
    void allModeUsesAllCategoryQuery() {
        when(transactionRepository.findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(row(1, "Rent", "R", "1200.00", 1L)));

        CategoryAggregationResult result = service.aggregateExpenses(
                WALLET_ID, USER_ID, START, END, CategoryAggregationMode.ALL);

        assertThat(result.total()).isEqualByComparingTo("1200.00");
        assertThat(result.categories()).hasSize(1);
        verify(transactionRepository).findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE);
    }

    @Test
    void emptyRowsProduceZeroTotalAndNoPercentages() {
        when(transactionRepository.findCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of());

        CategoryAggregationResult result = service.aggregateExpenses(
                WALLET_ID, USER_ID, START, END, CategoryAggregationMode.ALL);

        assertThat(result.total()).isEqualByComparingTo("0.00");
        assertThat(result.categories()).isEmpty();
    }

    @Test
    void equalAmountCategoriesKeepRepositoryDeterministicOrder() {
        when(transactionRepository.findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(
                        row(1, "Coffee", "C", "100.00", 1L),
                        row(2, "Groceries", "G", "100.00", 1L)));

        CategoryAggregationResult result = service.aggregateExpenses(
                WALLET_ID, USER_ID, START, END, CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result.categories()).extracting(CategoryAggregation::name)
                .containsExactly("Coffee", "Groceries");
    }

    @Test
    void comparisonUsesSameAggregationRulesForCurrentAndComparePeriods() {
        LocalDate compareStart = LocalDate.of(2026, 2, 1);
        LocalDate compareEnd = LocalDate.of(2026, 2, 28);
        when(transactionRepository.findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, START, END, CategoryType.EXPENSE))
                .thenReturn(List.of(row(1, "Food", "F", "600.00", 3L)));
        when(transactionRepository.findIncludedCategorySpendByWalletUserAndDateRange(
                WALLET_ID, USER_ID, compareStart, compareEnd, CategoryType.EXPENSE))
                .thenReturn(List.of(row(1, "Food", "F", "400.00", 2L)));

        List<CategoryComparisonAggregation> result = service.aggregateExpenseComparison(
                WALLET_ID, USER_ID, START, END, compareStart, compareEnd,
                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().category().amount()).isEqualByComparingTo("600.00");
        assertThat(result.getFirst().compareAmount()).isEqualByComparingTo("400.00");
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
