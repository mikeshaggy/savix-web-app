package com.mikeshaggy.backend.dashboard.service.calculator;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.CategorySpendingDto;
import com.mikeshaggy.backend.dashboard.dto.PercentageChangeDto;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.*;

@Component
public class TopCategoriesCalculator {

    private static final int TOP_N = 5;

    public List<CategorySpendingDto> calculate(List<Transaction> currentTransactions,
                                                List<Transaction> compareTransactions) {
        Map<Integer, BigDecimal> currentByCategory = groupExpensesByCategory(currentTransactions);
        Map<Integer, BigDecimal> compareByCategory = groupExpensesByCategory(compareTransactions);
        Map<Integer, String> currentCategoryNames = categoryNamesById(currentTransactions);

        BigDecimal totalExpenses = currentByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return currentByCategory.entrySet().stream()
                .sorted(Map.Entry.<Integer, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_N)
                .map(entry -> {
                    Integer categoryId = entry.getKey();
                    BigDecimal amount = entry.getValue().setScale(SCALE, ROUNDING);
                    BigDecimal percentageOfTotal = calculatePercentage(amount, totalExpenses);
                    BigDecimal previousAmount = compareByCategory.getOrDefault(categoryId, BigDecimal.ZERO);
                    PercentageChangeDto change = percentageChange(amount, previousAmount);

                    return new CategorySpendingDto(currentCategoryNames.get(categoryId), amount, percentageOfTotal, change);
                })
                .toList();
    }

    private Map<Integer, BigDecimal> groupExpensesByCategory(List<Transaction> transactions) {
        return transactions.stream()
                .filter(this::isTopCategoryExpense)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().getId(),
                        Collectors.reducing(BigDecimal.ZERO,
                                Transaction::getAmount,
                                BigDecimal::add)
                ));
    }

    private Map<Integer, String> categoryNamesById(List<Transaction> transactions) {
        return transactions.stream()
                .filter(this::isTopCategoryExpense)
                .collect(Collectors.toMap(
                        t -> t.getCategory().getId(),
                        t -> t.getCategory().getName(),
                        (existing, ignored) -> existing
                ));
    }

    private boolean isTopCategoryExpense(Transaction transaction) {
        return transaction.getCategory().getType() == CategoryType.EXPENSE
                && !transaction.getCategory().isExcludedFromTopCategories();
    }

    private BigDecimal calculatePercentage(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return amount.multiply(HUNDRED).divide(total, SCALE, ROUNDING);
    }

}
