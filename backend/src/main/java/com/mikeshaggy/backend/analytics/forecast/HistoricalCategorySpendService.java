package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyCategoryTotal;
import com.mikeshaggy.backend.common.calculation.Quantiles;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.zero;

/**
 * Stage 7.3 — per-category pace-eligible variable spend at cycle day {@code d}, against the <em>median</em> of the same
 * day in the last {@value HistoricalVariableSpendService#DEFAULT_CYCLES} closed cycles (decision I2: multi-cycle median at
 * the same cycle day, never "previous period", never an average).
 * <p>
 * Semantics match the Stage 4 / 6.3 baseline ({@link HistoricalVariableSpendService},
 * {@code HistoricalBaseline#typicalCumulativeAt}):
 * <ul>
 *   <li>Spend = pace-eligible variable EXPENSE transactions (unlinked to a fixed payment, not excluded from the pace on
 *       the transaction or its category) — {@code AnalyticsTransactionQueryService#dailyCategoryVariableTotals}.</li>
 *   <li>{@code d} = 1-based day index of {@code today} in the current cycle; the current amount runs from the cycle start
 *       through {@code today} (a future-dated transaction never counts).</li>
 *   <li>A closed cycle contributes its cumulative spend over its own days {@code 1..d}; a cycle shorter than {@code d}
 *       is dropped, never padded. A usable cycle without spend in the category contributes {@code 0.00} — zeros are
 *       observations and pull the median down.</li>
 * </ul>
 * One query covers the current cycle and the whole history. Not memoised; read-only.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoricalCategorySpendService {

    private final PayCycleService payCycleService;
    private final AnalyticsTransactionQueryService analyticsTransactionQueryService;

    /**
     * @param currentCycle the salary wallet's current cycle; only closed cycles ending before its start are used
     * @param today        the viewing day, on or after {@code currentCycle.start()}
     */
    public CategoryDayBaseline atDay(UUID userId, Integer walletId, PayCycle currentCycle, LocalDate today) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(walletId, "walletId");
        Objects.requireNonNull(currentCycle, "currentCycle");
        Objects.requireNonNull(today, "today");
        int dayIndex = currentCycle.dayIndex(today);
        if (dayIndex < 1) {
            throw new IllegalArgumentException("today " + today + " is before the cycle start " + currentCycle.start());
        }

        List<PayCycle> usable = payCycleService.history(userId, HistoricalVariableSpendService.DEFAULT_CYCLES).stream()
                .filter(cycle -> cycle.state() == CycleState.CLOSED)
                .filter(cycle -> cycle.end().isBefore(currentCycle.start()))
                .limit(HistoricalVariableSpendService.DEFAULT_CYCLES)
                .filter(cycle -> cycle.lengthDays() >= dayIndex)
                .toList();

        LocalDate from = usable.isEmpty() ? currentCycle.start() : usable.getLast().start();
        List<DailyCategoryTotal> rows = analyticsTransactionQueryService.dailyCategoryVariableTotals(
                walletId, userId, from, today);

        Map<Integer, CategoryAccumulator> categories = new LinkedHashMap<>();
        for (DailyCategoryTotal row : rows) {
            CategoryAccumulator category = categories.computeIfAbsent(row.categoryId(),
                    id -> new CategoryAccumulator(row.categoryId(), row.name(), row.emoji(), usable.size()));
            if (!row.day().isBefore(currentCycle.start())) {
                category.current = category.current.add(row.amount());
                continue;
            }
            for (int i = 0; i < usable.size(); i++) {
                PayCycle cycle = usable.get(i);
                LocalDate cutoff = cycle.start().plusDays(dayIndex - 1L);
                if (!row.day().isBefore(cycle.start()) && !row.day().isAfter(cutoff)) {
                    category.perCycle[i] = category.perCycle[i].add(row.amount());
                }
            }
        }

        List<CategoryAtDay> result = new ArrayList<>(categories.size());
        for (CategoryAccumulator category : categories.values()) {
            List<BigDecimal> perCycle = new ArrayList<>(usable.size());
            for (BigDecimal amount : category.perCycle) {
                perCycle.add(money(amount));
            }
            BigDecimal median = perCycle.isEmpty() ? null : Quantiles.median(perCycle);
            result.add(new CategoryAtDay(category.categoryId, category.name, category.emoji, money(category.current),
                    median, List.copyOf(perCycle)));
        }
        result.sort(Comparator.comparing(CategoryAtDay::categoryId));
        return new CategoryDayBaseline(dayIndex, usable, List.copyOf(result));
    }

    /**
     * @param cycles     the usable closed cycles (reached day {@code dayIndex}), newest first
     * @param categories every category with eligible spend in the current cycle or a usable cycle, by id
     */
    public record CategoryDayBaseline(int dayIndex, List<PayCycle> cycles, List<CategoryAtDay> categories) {

        public CategoryDayBaseline {
            cycles = List.copyOf(cycles);
            categories = List.copyOf(categories);
        }

        public int cyclesUsed() {
            return cycles.size();
        }
    }

    /**
     * @param currentAmount  eligible spend from the cycle start through today
     * @param medianAtDay    median of {@code perCycle}; {@code null} without a usable cycle
     * @param perCycle       cumulative day-1..d spend per usable cycle, aligned with {@link CategoryDayBaseline#cycles()}
     */
    public record CategoryAtDay(Integer categoryId, String name, String emoji, BigDecimal currentAmount,
                                BigDecimal medianAtDay, List<BigDecimal> perCycle) {
    }

    private static final class CategoryAccumulator {
        private final Integer categoryId;
        private final String name;
        private final String emoji;
        private final BigDecimal[] perCycle;
        private BigDecimal current = zero();

        private CategoryAccumulator(Integer categoryId, String name, String emoji, int cycles) {
            this.categoryId = categoryId;
            this.name = name;
            this.emoji = emoji;
            this.perCycle = new BigDecimal[cycles];
            Arrays.fill(perCycle, zero());
        }
    }
}
