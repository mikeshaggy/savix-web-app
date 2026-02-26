package com.mikeshaggy.backend.dashboard.service.calculator;

import com.mikeshaggy.backend.category.domain.Type;
import com.mikeshaggy.backend.dashboard.dto.PercentageChangeDto;
import com.mikeshaggy.backend.dashboard.dto.SummaryDto;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class SummaryCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

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
                .filter(t -> t.getCategory().getType() == Type.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, ROUNDING);
    }

    private BigDecimal sumExpenses(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getCategory().getType() == Type.EXPENSE)
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

    private PercentageChangeDto percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return new PercentageChangeDto(BigDecimal.ZERO.setScale(SCALE, ROUNDING), true);
        }

        BigDecimal change = current.subtract(previous)
                .multiply(HUNDRED)
                .divide(previous.abs(), SCALE, ROUNDING);

        return new PercentageChangeDto(change.abs(), change.compareTo(BigDecimal.ZERO) >= 0);
    }
}
