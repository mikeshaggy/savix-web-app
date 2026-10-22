package com.mikeshaggy.backend.analytics.query;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.repository.DailyTotalProjection;
import com.mikeshaggy.backend.transaction.repository.HeatmapProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Pace-eligible variable spend: unlinked EXPENSE transactions minus rows
     * excluded from the pace on the transaction or on its category
     * ({@code excludedFromPace}). Forecast v2 input only — reporting totals and
     * {@link #sumUnlinked} are unaffected by the exclusion flags.
     */
    public BigDecimal sumUnlinkedForPace(Integer walletId, UUID userId, LocalDate from, LocalDate to) {
        return money(transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRange(walletId, userId, from, to));
    }

    /**
     * Daily pace-eligible variable EXPENSE totals for every day in {@code [from, to]}
     * (inclusive, ascending). Days without eligible transactions are present with
     * zero so callers can take medians over the full window. Empty when
     * {@code from} is after {@code to}.
     */
    public List<DailyTotal> dailyVariableTotals(Integer walletId, UUID userId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> byDay = transactionRepository
                .findDailyPaceEligibleVariableTotals(walletId, userId, from, to)
                .stream()
                .collect(Collectors.toMap(DailyTotalProjection::getDay, DailyTotalProjection::getAmount, BigDecimal::add));

        List<DailyTotal> totals = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            totals.add(new DailyTotal(day, money(byDay.getOrDefault(day, BigDecimal.ZERO))));
        }
        return List.copyOf(totals);
    }

    /**
     * Unlinked EXPENSE transactions in {@code [from, to]} for the one-off explanation layer (Stage 4.6),
     * ascending by date then id. Unlike {@link #dailyVariableTotals} the exclusion flags do not filter here:
     * every row is returned and {@link VariableExpense#excluded()} says whether the pace already leaves it out
     * (transaction or category flag, {@code Transaction#isPaceExcluded()}).
     */
    public List<VariableExpense> unlinkedExpenses(Integer walletId, UUID userId, LocalDate from, LocalDate to) {
        return transactionRepository.findUnlinkedExpensesByWalletUserDateRange(walletId, userId, from, to).stream()
                .map(t -> new VariableExpense(t.getId(), t.getTransactionDate(), t.getTitle(),
                        t.getCategory().getName(), money(t.getAmount()), t.isPaceExcluded()))
                .toList();
    }

    /**
     * The same unlinked EXPENSE rows as {@link #unlinkedExpenses} (exclusion flags do not filter), with the category
     * id — the Stage 7.3 recurring-merchant input, which needs the category to prefill a fixed payment.
     */
    public List<UnlinkedExpense> unlinkedExpenseRows(Integer walletId, UUID userId, LocalDate from, LocalDate to) {
        return transactionRepository.findUnlinkedExpensesByWalletUserDateRange(walletId, userId, from, to).stream()
                .map(t -> new UnlinkedExpense(t.getId(), t.getTransactionDate(), t.getTitle(),
                        t.getCategory().getId(), t.getCategory().getName(), money(t.getAmount())))
                .toList();
    }

    /**
     * Pace-eligible variable spend ({@link #sumUnlinkedForPace}) classified with {@code importance} — always a part of
     * {@code sumUnlinkedForPace} over the same window.
     */
    public BigDecimal sumUnlinkedForPaceByImportance(Integer walletId, UUID userId, LocalDate from, LocalDate to,
                                                     Importance importance) {
        return money(transactionRepository.sumUnlinkedPaceEligibleByWalletUserDateRangeAndImportance(
                walletId, userId, from, to, importance));
    }

    /**
     * Per-day, per-category pace-eligible variable EXPENSE totals in {@code [from, to]} — the category split of
     * {@link #dailyVariableTotals} (not zero-filled: only days a category was spent on).
     */
    public List<DailyCategoryTotal> dailyCategoryVariableTotals(Integer walletId, UUID userId, LocalDate from,
                                                                LocalDate to) {
        return transactionRepository.findDailyPaceEligibleCategoryTotals(walletId, userId, from, to).stream()
                .map(row -> new DailyCategoryTotal(row.getDate(), row.getCategoryId(), row.getName(), row.getEmoji(),
                        money(row.getAmount())))
                .toList();
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

    public record DailyTotal(LocalDate day, BigDecimal amount) {
    }

    /** One unlinked expense as seen by the one-off layer; {@code excluded} = already out of the spending pace. */
    public record VariableExpense(
            Long transactionId,
            LocalDate date,
            String title,
            String categoryName,
            BigDecimal amount,
            boolean excluded) {
    }

    /** One unlinked expense with its category (Stage 7.3 recurring-merchant input). */
    public record UnlinkedExpense(
            Long transactionId,
            LocalDate date,
            String title,
            Integer categoryId,
            String categoryName,
            BigDecimal amount) {
    }

    public record DailyCategoryTotal(LocalDate day, Integer categoryId, String name, String emoji, BigDecimal amount) {
    }

    public record DailyExpenseStats(
            LocalDate highestSpendingDay,
            BigDecimal highestSpendingDayAmount,
            int activeDays) {
    }
}
