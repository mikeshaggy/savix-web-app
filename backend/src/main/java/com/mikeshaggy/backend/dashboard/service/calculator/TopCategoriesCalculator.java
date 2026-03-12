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

import static com.mikeshaggy.backend.dashboard.service.calculator.CalculationUtils.*;

@Component
public class TopCategoriesCalculator {

    private static final int TOP_N = 5;

    public List<CategorySpendingDto> calculate(List<Transaction> currentTransactions,
                                                List<Transaction> compareTransactions) {
        Map<String, BigDecimal> currentByCategory = groupExpensesByCategory(currentTransactions);
        Map<String, BigDecimal> compareByCategory = groupExpensesByCategory(compareTransactions);

        BigDecimal totalExpenses = currentByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return currentByCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .limit(TOP_N)
                .map(entry -> {
                    String categoryName = entry.getKey();
                    BigDecimal amount = entry.getValue().setScale(SCALE, ROUNDING);
                    BigDecimal percentageOfTotal = calculatePercentage(amount, totalExpenses);
                    BigDecimal previousAmount = compareByCategory.getOrDefault(categoryName, BigDecimal.ZERO);
                    PercentageChangeDto change = percentageChange(amount, previousAmount);

                    return new CategorySpendingDto(categoryName, amount, percentageOfTotal, change);
                })
                .toList();
    }

    private Map<String, BigDecimal> groupExpensesByCategory(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getCategory().getType() == CategoryType.EXPENSE)
                // TODO: implement proper category exclusion mechanism
                .filter(t -> !t.getCategory().getName().equalsIgnoreCase("rent")) // temp solution
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().getName(),
                        Collectors.reducing(BigDecimal.ZERO,
                                Transaction::getAmount,
                                BigDecimal::add)
                ));
    }

    private BigDecimal calculatePercentage(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return amount.multiply(HUNDRED).divide(total, SCALE, ROUNDING);
    }
}
