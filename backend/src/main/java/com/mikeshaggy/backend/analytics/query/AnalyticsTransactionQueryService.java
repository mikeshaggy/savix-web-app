package com.mikeshaggy.backend.analytics.query;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.repository.HeatmapProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsTransactionQueryService {

    private final TransactionRepository transactionRepository;

    public BigDecimal sum(Integer walletId, UUID userId, LocalDate from, LocalDate to, CategoryType type) {
        return money(transactionRepository.sumByWalletUserDateRangeAndType(walletId, userId, from, to, type));
    }

    /**
     * Sum of "variable" spend of the given type — transactions NOT linked to a
     * fixed payment occurrence. Used by the forecast to keep fixed payments out
     * of the variable daily burn rate.
     */
    public BigDecimal sumUnlinked(Integer walletId, UUID userId, LocalDate from, LocalDate to, CategoryType type) {
        return money(transactionRepository.sumUnlinkedByWalletUserDateRangeAndType(walletId, userId, from, to, type));
    }

    public BigDecimal expenseByImportance(Integer walletId, UUID userId,
                                          LocalDate from, LocalDate to,
                                          Importance importance) {
        return money(transactionRepository.sumByWalletUserDateRangeTypeAndImportance(
                walletId, userId, from, to, CategoryType.EXPENSE, importance));
    }

    public PeriodTotals totals(Integer walletId, UUID userId, LocalDate from, LocalDate to) {
        return new PeriodTotals(
                sum(walletId, userId, from, to, CategoryType.INCOME),
                sum(walletId, userId, from, to, CategoryType.EXPENSE));
    }

    public long count(Integer walletId, UUID userId, LocalDate from, LocalDate to) {
        return transactionRepository.countByWalletUserDateRange(walletId, userId, from, to);
    }

    public DailyExpenseStats dailyExpenseStats(Integer walletId, UUID userId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> dailyTotals = transactionRepository
                .findHeatmapByWalletDateRangeAndType(walletId, userId, from, to, CategoryType.EXPENSE)
                .stream()
                .collect(Collectors.groupingBy(
                        HeatmapProjection::getDate,
                        Collectors.reducing(BigDecimal.ZERO, HeatmapProjection::getAmount, BigDecimal::add)));

        if (dailyTotals.isEmpty()) {
            return new DailyExpenseStats(null, null, 0);
        }

        Map.Entry<LocalDate, BigDecimal> peak = dailyTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();

        return new DailyExpenseStats(peak.getKey(), money(peak.getValue()), dailyTotals.size());
    }

    public record PeriodTotals(BigDecimal income, BigDecimal expenses) {
        public BigDecimal balance() {
            return money(income.subtract(expenses));
        }
    }

    public record DailyExpenseStats(
            LocalDate highestSpendingDay,
            BigDecimal highestSpendingDayAmount,
            int activeDays) {
    }
}
