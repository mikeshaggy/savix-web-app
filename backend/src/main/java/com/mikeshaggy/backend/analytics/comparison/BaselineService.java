package com.mikeshaggy.backend.analytics.comparison;

import com.mikeshaggy.backend.analytics.comparison.BaselineCategoryComparisonDto;
import com.mikeshaggy.backend.analytics.comparison.BaselineComparisonDto;
import com.mikeshaggy.backend.analytics.comparison.BaselineDeltaDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.dto.ResolvedPeriods;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.transaction.repository.CategoryBreakdownProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BaselineService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PeriodService periodService;

    public BaselineComparisonDto getBaseline(Integer walletId, UUID userId,
                                             PeriodType periodType, LocalDate startDate, LocalDate endDate) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        ResolvedPeriods resolved = periodService.resolvePeriods(periodType, walletId, userId, startDate, endDate);
        PeriodDto primary = resolved.primary();
        PeriodDto compare = resolved.compare();

        BigDecimal currentIncome = money(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, primary.startDate(), primary.endDate(), CategoryType.INCOME));
        BigDecimal currentExpenses = money(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, primary.startDate(), primary.endDate(), CategoryType.EXPENSE));
        BigDecimal baselineIncome = money(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, compare.startDate(), compare.endDate(), CategoryType.INCOME));
        BigDecimal baselineExpenses = money(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, compare.startDate(), compare.endDate(), CategoryType.EXPENSE));

        BigDecimal currentSavingsRateForDelta = savingsRate(currentIncome, currentExpenses, 6);
        BigDecimal baselineSavingsRateForDelta = savingsRate(baselineIncome, baselineExpenses, 6);
        BigDecimal currentSavingsRate = money(currentSavingsRateForDelta);
        BigDecimal baselineSavingsRate = money(baselineSavingsRateForDelta);

        BaselineDeltaDto incomeDelta = delta(currentIncome, baselineIncome);
        BaselineDeltaDto expensesDelta = delta(currentExpenses, baselineExpenses);
        BaselineDeltaDto savingsRateDelta = delta(currentSavingsRateForDelta, baselineSavingsRateForDelta);

        List<CategoryBreakdownProjection> currentCategories = transactionRepository
                .findCategoryBreakdownByWalletDateRangeAndType(
                        walletId, primary.startDate(), primary.endDate(), CategoryType.EXPENSE);
        List<CategoryBreakdownProjection> compareCategories = transactionRepository
                .findCategoryBreakdownByWalletDateRangeAndType(
                        walletId, compare.startDate(), compare.endDate(), CategoryType.EXPENSE);

        return new BaselineComparisonDto(
                primary.startDate(), primary.endDate(),
                compare.startDate(), compare.endDate(),
                baselineIncome, baselineExpenses, baselineSavingsRate,
                currentIncome, currentExpenses, currentSavingsRate,
                incomeDelta.percent(), incomeDelta.display(), incomeDelta.available(),
                expensesDelta.percent(), expensesDelta.display(), expensesDelta.available(),
                savingsRateDelta.percent(), savingsRateDelta.display(), savingsRateDelta.available(),
                categoryComparisons(currentCategories, compareCategories));
    }

    private List<BaselineCategoryComparisonDto> categoryComparisons(
            List<CategoryBreakdownProjection> current,
            List<CategoryBreakdownProjection> compare) {
        Map<Integer, CategoryBreakdownProjection> currentMap = current.stream()
                .collect(Collectors.toMap(CategoryBreakdownProjection::getCategoryId, Function.identity()));
        Map<Integer, CategoryBreakdownProjection> compareMap = compare.stream()
                .collect(Collectors.toMap(CategoryBreakdownProjection::getCategoryId, Function.identity()));

        // Maintain insertion order: current-period categories first, then compare-only
        Map<Integer, Object> orderedIds = new LinkedHashMap<>();
        current.forEach(c -> orderedIds.put(c.getCategoryId(), c));
        compare.forEach(c -> orderedIds.putIfAbsent(c.getCategoryId(), c));

        return orderedIds.keySet().stream()
                .map(id -> {
                    CategoryBreakdownProjection curr = currentMap.get(id);
                    CategoryBreakdownProjection comp = compareMap.get(id);
                    String name = curr != null ? curr.getName() : comp.getName();
                    String emoji = curr != null ? curr.getEmoji() : comp.getEmoji();
                    BigDecimal currentAmt = money(curr != null ? curr.getAmount() : null);
                    BigDecimal compareAmt = money(comp != null ? comp.getAmount() : null);
                    BaselineDeltaDto d = delta(currentAmt, compareAmt);
                    return new BaselineCategoryComparisonDto(
                            id, name, emoji, compareAmt, currentAmt,
                            d.percent(), d.display(), d.available());
                })
                .filter(cat -> cat.baselineExpenses().compareTo(BigDecimal.ZERO) > 0
                        || cat.currentExpenses().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(BaselineCategoryComparisonDto::currentExpenses).reversed()
                        .thenComparing(BaselineCategoryComparisonDto::baselineExpenses, Comparator.reverseOrder())
                        .thenComparing(BaselineCategoryComparisonDto::name))
                .toList();
    }

    private BigDecimal savingsRate(BigDecimal income, BigDecimal expenses, int scale) {
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(scale, ROUNDING);
        }
        return income.subtract(expenses).multiply(HUNDRED).divide(income, scale, ROUNDING);
    }

    private BaselineDeltaDto delta(BigDecimal current, BigDecimal baseline) {
        if (baseline.compareTo(BigDecimal.ZERO) == 0) {
            if (current.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal percent = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
                return new BaselineDeltaDto(percent, formatDelta(percent), true);
            }
            return new BaselineDeltaDto(null, "N/A", false);
        }
        BigDecimal change = current.subtract(baseline)
                .multiply(HUNDRED)
                .divide(baseline, SCALE, ROUNDING);
        return new BaselineDeltaDto(change, formatDelta(change), true);
    }

    private String formatDelta(BigDecimal value) {
        return "%s%s%%".formatted(value.signum() > 0 ? "+" : "", value.setScale(SCALE, ROUNDING));
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
    }
}
