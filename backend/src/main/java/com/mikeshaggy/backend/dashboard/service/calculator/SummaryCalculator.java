package com.mikeshaggy.backend.dashboard.service.calculator;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.SummaryDto;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

import static com.mikeshaggy.backend.dashboard.service.calculator.CalculationUtils.*;

@Component
public class SummaryCalculator {

    public SummaryDto calculate(List<Transaction> currentTransactions,
                                List<Transaction> compareTransactions) {
        BigDecimal income = sumIncome(currentTransactions);
        BigDecimal expenses = sumExpenses(currentTransactions);
        BigDecimal saved = income.subtract(expenses);
        BigDecimal savingsRate = calculateSavingsRate(income, saved);

        BigDecimal prevIncome = sumIncome(compareTransactions);
        BigDecimal prevExpenses = sumExpenses(compareTransactions);
        BigDecimal prevSaved = prevIncome.subtract(prevExpenses);

        return new SummaryDto(
                income,
                expenses,
                saved,
                savingsRate,
                percentageChange(income, prevIncome),
                percentageChange(expenses, prevExpenses),
                percentageChange(saved, prevSaved)
        );
    }

    private BigDecimal sumIncome(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getCategory().getType() == CategoryType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, ROUNDING);
    }

    private BigDecimal sumExpenses(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getCategory().getType() == CategoryType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, ROUNDING);
    }

    private BigDecimal calculateSavingsRate(BigDecimal income, BigDecimal saved) {
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return saved.multiply(HUNDRED).divide(income, SCALE, ROUNDING);
    }
}
