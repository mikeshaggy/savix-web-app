package com.mikeshaggy.backend.analytics.aggregation;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.repository.CategoryBreakdownProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryAggregationService {

    private final TransactionRepository transactionRepository;

    public CategoryAggregationResult aggregateExpenses(Integer walletId, UUID userId,
                                                       LocalDate from, LocalDate to,
                                                       CategoryAggregationMode mode) {
        List<CategoryBreakdownProjection> rows = fetchRows(walletId, userId, from, to, mode);
        BigDecimal total = rows.stream()
                .map(row -> money(row.getAmount()))
                .reduce(zero(), BigDecimal::add)
                .setScale(SCALE, ROUNDING);

        List<CategoryAggregation> categories = rows.stream()
                .map(row -> new CategoryAggregation(
                        row.getCategoryId(),
                        row.getName(),
                        row.getEmoji(),
                        money(row.getAmount()),
                        share(row.getAmount(), total),
                        row.getTransactionCount()))
                .toList();

        return new CategoryAggregationResult(total, categories);
    }

    public List<CategoryComparisonAggregation> aggregateExpenseComparison(
            Integer walletId, UUID userId,
            LocalDate currentFrom, LocalDate currentTo,
            LocalDate compareFrom, LocalDate compareTo,
            CategoryAggregationMode mode) {
        CategoryAggregationResult current = aggregateExpenses(walletId, userId, currentFrom, currentTo, mode);
        CategoryAggregationResult compare = aggregateExpenses(walletId, userId, compareFrom, compareTo, mode);

        Map<Integer, BigDecimal> compareByCategory = compare.categories().stream()
                .collect(Collectors.toMap(
                        CategoryAggregation::categoryId,
                        CategoryAggregation::amount));

        return current.categories().stream()
                .map(category -> new CategoryComparisonAggregation(
                        category,
                        compareByCategory.getOrDefault(category.categoryId(), zero())))
                .toList();
    }

    private List<CategoryBreakdownProjection> fetchRows(Integer walletId, UUID userId,
                                                        LocalDate from, LocalDate to,
                                                        CategoryAggregationMode mode) {
        return switch (mode) {
            case ALL -> transactionRepository.findCategorySpendByWalletUserAndDateRange(
                    walletId, userId, from, to, CategoryType.EXPENSE);
            case INCLUDED_IN_TOP_CATEGORIES -> transactionRepository.findIncludedCategorySpendByWalletUserAndDateRange(
                    walletId, userId, from, to, CategoryType.EXPENSE);
        };
    }

    private BigDecimal share(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return zero();
        }
        return money(amount).multiply(HUNDRED).divide(total, SCALE, ROUNDING);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    }
}
