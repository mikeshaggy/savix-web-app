package com.mikeshaggy.backend.analytics.cyclecomparison;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.insight.InsightDto;
import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.repository.CategoryRepository;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.DailyCategorySpendProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CycleComparisonService {

    private static final int DEFAULT_BASELINE_CYCLES = 3;
    private static final int MIN_BASELINE_CYCLES = 1;
    private static final int MAX_BASELINE_CYCLES = 12;
    private static final BigDecimal IN_LINE_THRESHOLD_PERCENT = new BigDecimal("5.00");

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public CycleComparisonResponseDto getCycleComparison(
            Integer walletId,
            UUID userId,
            LocalDate requestedAsOfDate,
            Integer requestedBaselineCycles,
            List<Integer> categoryIds,
            CategoryAggregationMode categoryMode,
            boolean includeIncome,
            List<Importance> importance) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        if (includeIncome) {
            throw new IllegalArgumentException(
                    "includeIncome=true is not supported by cycle-comparison because this endpoint compares expense spending pace only");
        }

        int baselineCycles = normalizeBaselineCycles(requestedBaselineCycles);
        LocalDate asOfDate = requestedAsOfDate == null ? LocalDate.now(clock) : requestedAsOfDate;
        CategoryAggregationMode mode = categoryMode == null ? CategoryAggregationMode.ALL : categoryMode;

        CyclePlan cyclePlan = resolveCycles(walletId, userId, asOfDate, baselineCycles);
        if (asOfDate.isBefore(cyclePlan.current().startDate())) {
            throw new IllegalArgumentException("asOfDate must not be before the current cycle start date");
        }

        CycleWindow current = cyclePlan.current().withCutoff(asOfDate);
        int dayIndex = inclusiveDays(current.startDate(), current.cutoffDate());

        List<CycleWindow> baselineWindows = cyclePlan.baseline().stream()
                .map(cycle -> cycle.withCutoff(cycle.startDate().plusDays(dayIndex - 1)))
                .toList();

        LocalDate from = baselineWindows.stream()
                .map(CycleWindow::startDate)
                .min(LocalDate::compareTo)
                .orElse(current.startDate());
        LocalDate to = current.cutoffDate();

        List<DailyCategorySpendProjection> rows = transactionRepository
                .findDailyCategorySpendByWalletUserDateRangeAndType(
                        walletId,
                        userId,
                        from,
                        to,
                        CategoryType.EXPENSE,
                        safeIds(categoryIds),
                        categoryIds == null || categoryIds.isEmpty(),
                        mode == CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES,
                        safeImportance(importance),
                        importance == null || importance.isEmpty());

        CycleAggregation currentAggregation = aggregateCycle(rows, current);
        List<CycleAggregation> baselineAggregations = baselineWindows.stream()
                .map(cycle -> aggregateCycle(rows, cycle))
                .toList();

        BigDecimal baselineAverageExpenses = average(
                baselineAggregations.stream().map(CycleAggregation::total).toList(),
                baselineAggregations.size());
        CycleComparisonSummaryDto summary = summary(
                currentAggregation.total(), baselineAverageExpenses, baselineAggregations.size());

        List<CycleComparisonBaselineCycleDto> baselineCycleDtos = new ArrayList<>();
        for (int i = 0; i < baselineWindows.size(); i++) {
            CycleWindow cycle = baselineWindows.get(i);
            CycleAggregation aggregation = baselineAggregations.get(i);
            baselineCycleDtos.add(new CycleComparisonBaselineCycleDto(
                    cycle.startDate(),
                    cycle.endDate(),
                    cycle.cutoffDate(),
                    inclusiveDays(cycle.startDate(), cycle.endDate()),
                    inclusiveDays(cycle.startDate(), cycle.cutoffDate()),
                    aggregation.total()));
        }

        List<CycleComparisonCategoryDto> categories = categories(
                currentAggregation.categories(), baselineAggregations, baselineAggregations.size());

        return new CycleComparisonResponseDto(
                walletId,
                asOfDate,
                new CycleComparisonCycleDto(
                        current.startDate(),
                        current.endDate(),
                        current.cutoffDate(),
                        dayIndex,
                        dayIndex,
                        inclusiveDays(current.startDate(), current.endDate())),
                new CycleComparisonBaselineDto(
                        baselineCycles,
                        baselineAggregations.size(),
                        !baselineAggregations.isEmpty(),
                        baselineCycleDtos),
                summary,
                categories,
                new CycleComparisonSeriesDto(
                        currentSeries(rows, current, dayIndex),
                        baselineSeries(rows, baselineWindows, dayIndex)),
                highlights(categories),
                List.<InsightDto>of());
    }

    private int normalizeBaselineCycles(Integer requestedBaselineCycles) {
        int value = requestedBaselineCycles == null ? DEFAULT_BASELINE_CYCLES : requestedBaselineCycles;
        if (value < MIN_BASELINE_CYCLES || value > MAX_BASELINE_CYCLES) {
            throw new IllegalArgumentException("baselineCycles must be between 1 and 12");
        }
        return value;
    }

    private CyclePlan resolveCycles(Integer walletId, UUID userId, LocalDate asOfDate, int baselineCycles) {
        Optional<Category> anchor = categoryRepository.findByUserIdAndIsCycleAnchorTrue(userId);
        if (anchor.isEmpty()) {
            return monthlyCycles(asOfDate, baselineCycles);
        }

        List<Transaction> anchorTransactions = transactionRepository
                .findByWalletUserCategoryAndDateLessThanEqualOrderByTransactionDateDesc(
                        walletId, userId, anchor.get().getId(), asOfDate, PageRequest.of(0, baselineCycles + 1));

        if (anchorTransactions.isEmpty()) {
            return monthlyCycles(asOfDate, baselineCycles);
        }

        List<LocalDate> anchorDates = anchorTransactions.stream()
                .map(Transaction::getTransactionDate)
                .distinct()
                .toList();

        LocalDate currentStart = anchorDates.getFirst();
        CycleWindow current = new CycleWindow(currentStart, currentStart.plusMonths(1).minusDays(1), asOfDate);

        List<CycleWindow> baseline = new ArrayList<>();
        for (int i = 1; i < anchorDates.size() && baseline.size() < baselineCycles; i++) {
            LocalDate start = anchorDates.get(i);
            LocalDate end = anchorDates.get(i - 1).minusDays(1);
            baseline.add(new CycleWindow(start, end, end));
        }

        return new CyclePlan(current, baseline);
    }

    private CyclePlan monthlyCycles(LocalDate asOfDate, int baselineCycles) {
        YearMonth currentMonth = YearMonth.from(asOfDate);
        CycleWindow current = new CycleWindow(currentMonth.atDay(1), currentMonth.atEndOfMonth(), asOfDate);

        List<CycleWindow> baseline = new ArrayList<>();
        for (int i = 1; i <= baselineCycles; i++) {
            YearMonth month = currentMonth.minusMonths(i);
            baseline.add(new CycleWindow(month.atDay(1), month.atEndOfMonth(), month.atEndOfMonth()));
        }
        return new CyclePlan(current, baseline);
    }

    private CycleAggregation aggregateCycle(List<DailyCategorySpendProjection> rows, CycleWindow cycle) {
        Map<Integer, CategoryAmount> categories = new LinkedHashMap<>();
        BigDecimal total = zero();

        for (DailyCategorySpendProjection row : rows) {
            LocalDate date = row.getDate();
            if (date.isBefore(cycle.startDate()) || date.isAfter(cycle.cutoffDate())) {
                continue;
            }
            BigDecimal amount = money(row.getAmount());
            total = total.add(amount).setScale(SCALE, ROUNDING);
            CategoryAmount existing = categories.get(row.getCategoryId());
            if (existing == null) {
                categories.put(row.getCategoryId(), new CategoryAmount(
                        row.getCategoryId(),
                        row.getName(),
                        row.getEmoji(),
                        amount,
                        count(row.getTransactionCount())));
            } else {
                categories.put(row.getCategoryId(), existing.plus(amount, count(row.getTransactionCount())));
            }
        }

        return new CycleAggregation(total, categories);
    }

    private List<CycleComparisonCategoryDto> categories(
            Map<Integer, CategoryAmount> current,
            List<CycleAggregation> baselineAggregations,
            int baselineCount) {
        Map<Integer, List<CategoryAmount>> baselineByCategory = baselineAggregations.stream()
                .flatMap(aggregation -> aggregation.categories().values().stream())
                .collect(Collectors.groupingBy(CategoryAmount::categoryId));

        Map<Integer, CategoryAmount> ordered = new LinkedHashMap<>();
        current.values().forEach(category -> ordered.put(category.categoryId(), category));
        baselineByCategory.values().stream()
                .flatMap(List::stream)
                .forEach(category -> ordered.putIfAbsent(category.categoryId(), category));

        return ordered.values().stream()
                .map(category -> {
                    CategoryAmount currentCategory = current.get(category.categoryId());
                    BigDecimal currentAmount = currentCategory == null ? zero() : currentCategory.amount();
                    long currentCount = currentCategory == null ? 0L : currentCategory.transactionCount();
                    List<CategoryAmount> baselineRows = baselineByCategory.getOrDefault(category.categoryId(), List.of());
                    BigDecimal baselineAmount = average(
                            baselineRows.stream().map(CategoryAmount::amount).toList(), baselineCount);
                    BigDecimal baselineTransactions = average(
                            baselineRows.stream()
                                    .map(row -> BigDecimal.valueOf(row.transactionCount()))
                                    .toList(),
                            baselineCount);
                    BigDecimal deltaAmount = money(currentAmount.subtract(baselineAmount));
                    BigDecimal deltaPercent = deltaPercent(deltaAmount, baselineAmount);
                    return new CycleComparisonCategoryDto(
                            category.categoryId(),
                            category.name(),
                            category.emoji(),
                            currentAmount,
                            baselineAmount,
                            deltaAmount,
                            deltaPercent,
                            currentCount,
                            baselineTransactions,
                            status(currentAmount, baselineAmount, deltaPercent, baselineCount));
                })
                .filter(category -> category.currentAmount().compareTo(BigDecimal.ZERO) > 0
                        || category.baselineAverageAmount().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(CycleComparisonCategoryDto::deltaAmount).reversed()
                        .thenComparing(CycleComparisonCategoryDto::currentAmount, Comparator.reverseOrder())
                        .thenComparing(CycleComparisonCategoryDto::name))
                .toList();
    }

    private CycleComparisonSummaryDto summary(BigDecimal current, BigDecimal baselineAverage, int baselineCount) {
        BigDecimal deltaAmount = money(current.subtract(baselineAverage));
        BigDecimal deltaPercent = deltaPercent(deltaAmount, baselineAverage);
        return new CycleComparisonSummaryDto(
                current,
                baselineAverage,
                deltaAmount,
                deltaPercent,
                status(current, baselineAverage, deltaPercent, baselineCount));
    }

    private List<CycleComparisonPointDto> currentSeries(
            List<DailyCategorySpendProjection> rows, CycleWindow current, int dayIndex) {
        Map<LocalDate, BigDecimal> totalsByDate = rows.stream()
                .filter(row -> !row.getDate().isBefore(current.startDate()) && !row.getDate().isAfter(current.cutoffDate()))
                .collect(Collectors.groupingBy(
                        DailyCategorySpendProjection::getDate,
                        Collectors.reducing(zero(), row -> money(row.getAmount()), BigDecimal::add)));

        List<CycleComparisonPointDto> points = new ArrayList<>();
        BigDecimal running = zero();
        for (int day = 1; day <= dayIndex; day++) {
            LocalDate date = current.startDate().plusDays(day - 1L);
            running = running.add(money(totalsByDate.get(date))).setScale(SCALE, ROUNDING);
            points.add(new CycleComparisonPointDto(day, date, running));
        }
        return points;
    }

    private List<CycleComparisonPointDto> baselineSeries(
            List<DailyCategorySpendProjection> rows, List<CycleWindow> baselineCycles, int dayIndex) {
        if (baselineCycles.isEmpty()) {
            return Stream.iterate(1, day -> day + 1)
                    .limit(dayIndex)
                    .map(day -> new CycleComparisonPointDto(day, null, zero()))
                    .toList();
        }

        List<List<BigDecimal>> perCycle = baselineCycles.stream()
                .map(cycle -> cumulativeAmounts(rows, cycle, dayIndex))
                .toList();

        List<CycleComparisonPointDto> points = new ArrayList<>();
        for (int day = 1; day <= dayIndex; day++) {
            int index = day - 1;
            BigDecimal total = perCycle.stream()
                    .map(cycle -> cycle.get(index))
                    .reduce(zero(), BigDecimal::add)
                    .setScale(SCALE, ROUNDING);
            points.add(new CycleComparisonPointDto(day, null,
                    total.divide(BigDecimal.valueOf(perCycle.size()), SCALE, ROUNDING)));
        }
        return points;
    }

    private List<BigDecimal> cumulativeAmounts(
            List<DailyCategorySpendProjection> rows, CycleWindow cycle, int dayIndex) {
        Map<LocalDate, BigDecimal> totalsByDate = rows.stream()
                .filter(row -> !row.getDate().isBefore(cycle.startDate()) && !row.getDate().isAfter(cycle.cutoffDate()))
                .collect(Collectors.groupingBy(
                        DailyCategorySpendProjection::getDate,
                        Collectors.reducing(zero(), row -> money(row.getAmount()), BigDecimal::add)));

        List<BigDecimal> result = new ArrayList<>();
        BigDecimal running = zero();
        for (int day = 1; day <= dayIndex; day++) {
            LocalDate date = cycle.startDate().plusDays(day - 1L);
            if (!date.isAfter(cycle.cutoffDate())) {
                running = running.add(money(totalsByDate.get(date))).setScale(SCALE, ROUNDING);
            }
            result.add(running);
        }
        return result;
    }

    private CycleComparisonHighlightsDto highlights(List<CycleComparisonCategoryDto> categories) {
        List<CycleComparisonCategoryDto> largestIncrease = categories.stream()
                .filter(category -> category.deltaAmount().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(CycleComparisonCategoryDto::deltaAmount).reversed())
                .limit(3)
                .toList();
        List<CycleComparisonCategoryDto> largestDecrease = categories.stream()
                .filter(category -> category.deltaAmount().compareTo(BigDecimal.ZERO) < 0)
                .sorted(Comparator.comparing(CycleComparisonCategoryDto::deltaAmount))
                .limit(3)
                .toList();
        return new CycleComparisonHighlightsDto(largestIncrease, largestDecrease);
    }

    private CycleComparisonStatus status(
            BigDecimal current, BigDecimal baseline, BigDecimal deltaPercent, int baselineCount) {
        if (baselineCount == 0) {
            return CycleComparisonStatus.NO_BASELINE;
        }
        if (baseline.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0
                    ? CycleComparisonStatus.NEW_SPEND
                    : CycleComparisonStatus.IN_LINE;
        }
        if (deltaPercent.abs().compareTo(IN_LINE_THRESHOLD_PERCENT) <= 0) {
            return CycleComparisonStatus.IN_LINE;
        }
        return deltaPercent.compareTo(BigDecimal.ZERO) > 0
                ? CycleComparisonStatus.ABOVE_BASELINE
                : CycleComparisonStatus.BELOW_BASELINE;
    }

    private BigDecimal deltaPercent(BigDecimal deltaAmount, BigDecimal baseline) {
        if (baseline.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return deltaAmount.multiply(HUNDRED).divide(baseline, SCALE, ROUNDING);
    }

    private BigDecimal average(List<BigDecimal> values, int divisor) {
        if (divisor == 0) {
            return zero();
        }
        BigDecimal total = values.stream()
                .filter(Objects::nonNull)
                .map(this::money)
                .reduce(zero(), BigDecimal::add)
                .setScale(SCALE, ROUNDING);
        return total.divide(BigDecimal.valueOf(divisor), SCALE, ROUNDING);
    }

    private List<Integer> safeIds(List<Integer> categoryIds) {
        return categoryIds == null || categoryIds.isEmpty() ? List.of(-1) : categoryIds;
    }

    private List<Importance> safeImportance(List<Importance> importance) {
        return importance == null || importance.isEmpty() ? List.of(Importance.ESSENTIAL) : importance;
    }

    private int inclusiveDays(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return 0;
        }
        return (int) (to.toEpochDay() - from.toEpochDay() + 1);
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    }

    private record CyclePlan(CycleWindow current, List<CycleWindow> baseline) {
    }

    private record CycleWindow(LocalDate startDate, LocalDate endDate, LocalDate cutoffDate) {
        CycleWindow withCutoff(LocalDate requestedCutoffDate) {
            LocalDate cutoff = requestedCutoffDate.isAfter(endDate) ? endDate : requestedCutoffDate;
            return new CycleWindow(startDate, endDate, cutoff);
        }
    }

    private record CycleAggregation(BigDecimal total, Map<Integer, CategoryAmount> categories) {
    }

    private record CategoryAmount(
            Integer categoryId,
            String name,
            String emoji,
            BigDecimal amount,
            long transactionCount) {

        CategoryAmount plus(BigDecimal additionalAmount, long additionalTransactions) {
            return new CategoryAmount(
                    categoryId,
                    name,
                    emoji,
                    amount.add(additionalAmount).setScale(SCALE, ROUNDING),
                    transactionCount + additionalTransactions);
        }
    }
}
