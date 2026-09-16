package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CurrentPace;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.ForecastInput;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.ForecastV2;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.VariableExpense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;

/**
 * Stage 4.6 — which current-cycle expenses look like one-offs, and what the forecast would say without each.
 * <p>
 * The candidates are <em>all</em> unlinked EXPENSE transactions from the cycle start through today
 * ({@code AnalyticsTransactionQueryService.unlinkedExpenses}), excluded rows included: the explanation layer
 * must still show a repayment the user already excluded, flagged {@code excluded = true}. The forecast
 * arithmetic itself only ever sees the pace-eligible daily series, so an excluded candidate has, by
 * construction, zero impact — it was never in the series to begin with.
 * <p>
 * <b>Denominator (decision 2026-09-16).</b> {@code variableExpensesToDate} ({@code V}) is the <em>gross</em> unlinked
 * EXPENSE spend of the cycle so far, before any pace exclusion — the sum of all candidates, which is the same number
 * as the v1 "variable expenses to date". Excluding a transaction therefore changes the forecast mathematics (it leaves
 * the pace series) but not its classification: an already-excluded row keeps the same {@code shareOfVariable} and
 * stays visible in the explanation layer. On the Sep 14 fixture the 300 PLN repayment is 19.33 % of 1,552.02 whether
 * or not it is excluded.
 * <p>
 * A transaction is flagged when {@code amount ≥ max(200, 0.15 × V)}; a day when its total {@code ≥ 0.40 × V}
 * (its largest transaction is reported). Equality qualifies. A transaction matching both rules is returned once.
 * Nothing is persisted.
 */
@Component
@RequiredArgsConstructor
public class OneOffDetector {

    static final BigDecimal MIN_ONE_OFF_AMOUNT = new BigDecimal("200.00");
    static final BigDecimal TRANSACTION_SHARE = new BigDecimal("0.15");
    static final BigDecimal DAY_SHARE = new BigDecimal("0.40");

    private final ForecastV2Calculator calculator;

    /**
     * @param candidates unlinked expenses in {@code [start, today]}, excluded rows included
     * @param daily      pace-eligible zero-filled series {@code start..today} the forecast is computed from
     * @param input      the Stage 4.5 input; its {@code currentPace} is replaced by the pace of {@code daily}
     *                   so the base forecast and every "without this one" forecast share one series
     * @return one-offs ordered by amount (desc), date (desc), id (asc)
     */
    public List<OneOff> detect(List<VariableExpense> candidates, List<DailyTotal> daily, ForecastInput input) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(daily, "daily");
        Objects.requireNonNull(input, "input");

        BigDecimal variableExpensesToDate = money(candidates.stream()
                .map(VariableExpense::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (variableExpensesToDate.signum() <= 0) {
            return List.of();
        }

        BigDecimal transactionThreshold = MIN_ONE_OFF_AMOUNT.max(TRANSACTION_SHARE.multiply(variableExpensesToDate));
        BigDecimal dayThreshold = DAY_SHARE.multiply(variableExpensesToDate);

        Map<Long, VariableExpense> flagged = new LinkedHashMap<>();
        for (VariableExpense candidate : candidates) {
            if (money(candidate.amount()).compareTo(transactionThreshold) >= 0) {
                flagged.putIfAbsent(candidate.transactionId(), candidate);
            }
        }
        Map<LocalDate, List<VariableExpense>> byDay = new LinkedHashMap<>();
        for (VariableExpense candidate : candidates) {
            byDay.computeIfAbsent(candidate.date(), d -> new ArrayList<>()).add(candidate);
        }
        for (List<VariableExpense> day : byDay.values()) {
            BigDecimal dayTotal = day.stream().map(VariableExpense::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (dayTotal.compareTo(dayThreshold) >= 0) {
                VariableExpense largest = day.stream()
                        .max(Comparator.comparing(VariableExpense::amount)
                                .thenComparing(VariableExpense::transactionId, Comparator.reverseOrder()))
                        .orElseThrow();
                flagged.putIfAbsent(largest.transactionId(), largest);
            }
        }
        if (flagged.isEmpty()) {
            return List.of();
        }

        ForecastInput baseInput = input.withCurrentPace(calculator.currentPace(daily, input.daysRemaining()));
        ForecastV2 base = calculator.forecast(baseInput);

        return flagged.values().stream()
                .map(candidate -> oneOff(candidate, variableExpensesToDate, daily, baseInput, base))
                .sorted(Comparator.comparing(OneOff::amount).reversed()
                        .thenComparing(OneOff::date, Comparator.reverseOrder())
                        .thenComparing(OneOff::transactionId))
                .toList();
    }

    private OneOff oneOff(VariableExpense candidate, BigDecimal variableExpensesToDate, List<DailyTotal> daily,
                          ForecastInput baseInput, ForecastV2 base) {
        BigDecimal amount = money(candidate.amount());
        BigDecimal share = amount.multiply(HUNDRED).divide(variableExpensesToDate, SCALE, ROUNDING);

        BigDecimal impactOnVariable;
        BigDecimal impactOnEndBalance;
        if (candidate.excluded()) {
            // already absent from the pace-eligible series: nothing to remove, nothing changes
            impactOnVariable = money(BigDecimal.ZERO);
            impactOnEndBalance = money(BigDecimal.ZERO);
        } else {
            CurrentPace without = calculator.currentPace(withoutTransaction(daily, candidate), baseInput.daysRemaining());
            ForecastV2 recomputed = calculator.forecast(baseInput.withCurrentPace(without));
            impactOnVariable = base.expectedVariableRemaining().subtract(recomputed.expectedVariableRemaining());
            impactOnEndBalance = recomputed.expectedEndBalanceTypical().subtract(base.expectedEndBalanceTypical());
        }

        return new OneOff(candidate.transactionId(), candidate.date(), candidate.title(), candidate.categoryName(),
                amount, share, candidate.excluded(), impactOnVariable, impactOnEndBalance);
    }

    /** The series with {@code candidate.amount()} taken off its day; the day must exist in {@code daily}. */
    private static List<DailyTotal> withoutTransaction(List<DailyTotal> daily, VariableExpense candidate) {
        boolean found = false;
        List<DailyTotal> result = new ArrayList<>(daily.size());
        for (DailyTotal day : daily) {
            if (day.day().equals(candidate.date())) {
                found = true;
                result.add(new DailyTotal(day.day(), money(day.amount().subtract(candidate.amount()))));
            } else {
                result.add(day);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("one-off candidate " + candidate.transactionId() + " dated "
                    + candidate.date() + " lies outside the daily series");
        }
        return result;
    }

    /**
     * @param shareOfVariable                    {@code amount / variableExpensesToDate × 100} — <b>percentage points</b>
     *                                           ({@code 19.33}, never the ratio {@code 0.1933}), 2 dp, against the gross
     *                                           denominator described on the class; the Stage 4.7 DTO must carry it as-is
     * @param excluded                           already excluded from the pace (transaction or category flag)
     * @param impactOnExpectedVariableRemaining  {@code expectedVariableRemaining(with) − (without)}: how much
     *                                           lower the expected remaining variable spend would be
     * @param impactOnExpectedEndBalance         {@code expectedEndBalanceTypical(without) − (with)}: how much
     *                                           higher the typical end balance would be; both {@code 0.00}
     *                                           for an excluded row
     */
    public record OneOff(
            Long transactionId,
            LocalDate date,
            String title,
            String categoryName,
            BigDecimal amount,
            BigDecimal shareOfVariable,
            boolean excluded,
            BigDecimal impactOnExpectedVariableRemaining,
            BigDecimal impactOnExpectedEndBalance) {
    }
}
