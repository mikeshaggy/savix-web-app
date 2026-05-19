package com.mikeshaggy.backend.analytics.service;

import com.mikeshaggy.backend.analytics.dto.MonthlyOverviewDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyOverviewService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final Clock clock;

    public MonthlyOverviewDto getMonthlyOverview(Integer walletId, UUID userId, YearMonth requestedMonth) {
        if (requestedMonth == null) {
            throw new IllegalArgumentException("month query parameter is required");
        }

        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));

        if (requestedMonth.isAfter(currentMonth)) {
            throw new IllegalArgumentException("month must not be in the future");
        }

        walletService.getWalletEntityByIdForUser(walletId, userId);

        LocalDate from = requestedMonth.atDay(1);
        LocalDate to = requestedMonth.atEndOfMonth();

        BigDecimal income = zeroIfNull(transactionRepository.sumByWalletDateRangeAndType(
                walletId, from, to, CategoryType.INCOME)).setScale(SCALE, ROUNDING);
        BigDecimal expenses = zeroIfNull(transactionRepository.sumByWalletDateRangeAndType(
                walletId, from, to, CategoryType.EXPENSE)).setScale(SCALE, ROUNDING);
        long transactionCount = transactionRepository.countByWalletDateRange(walletId, from, to);

        BigDecimal balance = income.subtract(expenses).setScale(SCALE, ROUNDING);
        BigDecimal savingsRate = calculateSavingsRate(balance, income);

        int daysInMonth = requestedMonth.lengthOfMonth();
        int daysElapsed = requestedMonth.equals(currentMonth)
                ? LocalDate.now(clock).getDayOfMonth()
                : daysInMonth;
        BigDecimal avgDailySpending = calculateAverageDailySpending(expenses, daysElapsed);

        return new MonthlyOverviewDto(
                requestedMonth.toString(),
                walletId,
                income,
                expenses,
                balance,
                savingsRate,
                transactionCount,
                avgDailySpending,
                daysInMonth,
                daysElapsed);
    }

    private BigDecimal calculateSavingsRate(BigDecimal balance, BigDecimal income) {
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        return balance.multiply(HUNDRED).divide(income, SCALE, ROUNDING);
    }

    private BigDecimal calculateAverageDailySpending(BigDecimal expenses, int daysElapsed) {
        return expenses.divide(BigDecimal.valueOf(daysElapsed), SCALE, ROUNDING);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
