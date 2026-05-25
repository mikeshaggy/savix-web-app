package com.mikeshaggy.backend.analytics.overview;

import com.mikeshaggy.backend.analytics.overview.PeriodOverviewDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeriodOverviewService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PeriodService periodService;
    private final Clock clock;

    public PeriodOverviewDto getPeriodOverview(Integer walletId, UUID userId,
                                               PeriodType periodType,
                                               LocalDate startDate, LocalDate endDate) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        PeriodDto period = periodService.resolve(periodType, walletId, userId, startDate, endDate);

        LocalDate from = period.startDate();
        LocalDate to = period.endDate();

        LocalDate today = LocalDate.now(clock);
        if (from.isAfter(today)) {
            throw new IllegalArgumentException("period must not be in the future");
        }

        BigDecimal income = zeroIfNull(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, from, to, CategoryType.INCOME)).setScale(SCALE, ROUNDING);
        BigDecimal expenses = zeroIfNull(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, from, to, CategoryType.EXPENSE)).setScale(SCALE, ROUNDING);
        long transactionCount = transactionRepository.countByWalletUserDateRange(walletId, userId, from, to);

        BigDecimal balance = income.subtract(expenses).setScale(SCALE, ROUNDING);
        BigDecimal savingsRate = calculateSavingsRate(balance, income);

        int daysInPeriod = (int) (to.toEpochDay() - from.toEpochDay() + 1);
        int daysElapsed = today.isAfter(to)
                ? daysInPeriod
                : (int) (today.toEpochDay() - from.toEpochDay() + 1);

        BigDecimal avgDailySpending = expenses.divide(BigDecimal.valueOf(daysElapsed), SCALE, ROUNDING);

        return new PeriodOverviewDto(from, to, walletId, income, expenses, balance,
                savingsRate, transactionCount, avgDailySpending, daysInPeriod, daysElapsed);
    }

    private BigDecimal calculateSavingsRate(BigDecimal balance, BigDecimal income) {
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return balance.multiply(HUNDRED).divide(income, SCALE, ROUNDING);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
