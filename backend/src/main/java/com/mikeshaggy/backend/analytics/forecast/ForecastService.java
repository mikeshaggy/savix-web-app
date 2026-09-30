package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CommittedOccurrence;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CurrentPace;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.ForecastInput;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.ForecastV2;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.HistoricalBaseline;
import com.mikeshaggy.backend.analytics.forecast.OneOffDetector.OneOff;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.VariableExpense;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;

/**
 * Stage 4.7 — the one place that assembles Forecast v2. Resolves applicability, takes the committed
 * occurrences from the Stage 3 tile (never re-bucketed here), loads the pace-eligible zero-filled daily series
 * <em>once</em> and hands that same list to {@link ForecastV2Calculator#currentPace}, the base forecast and
 * {@link OneOffDetector} (so the pace and its explanation cannot drift), computes the historical baseline, runs
 * the Stage 4.5 calculator and maps everything to {@link ForecastV2Dto}.
 *
 * <p>Applicable only to the salary wallet's {@code PAY_CYCLE} period as resolved by pay-cycle-v2: an
 * {@link CycleState#OPEN} cycle gets the full forecast; a cycle {@link CycleState#AWAITING_SALARY} has no
 * remaining horizon and gets the obligation-side shell only (status {@code null}, reason
 * {@code AWAITING_SALARY}); every other period — {@code MONTHLY}, {@code CUSTOM}, {@code LAST_PAY_CYCLE}, a
 * non-salary wallet, a legacy-resolved cycle without state — yields {@code null}. Feature flags are not consulted
 * here: the caller decides whether the result is exposed, shadow-logged or not computed at all.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ForecastService {

    private final AnalyticsTransactionQueryService transactionQueryService;
    private final FixedPaymentDashboardService fixedPaymentDashboardService;
    private final HistoricalVariableSpendService historicalVariableSpendService;
    private final ForecastV2Calculator calculator;
    private final OneOffDetector oneOffDetector;
    private final Clock clock;

    /** Salary-wallet {@code PAY_CYCLE} that is {@code OPEN} or {@code AWAITING_SALARY}; nothing else has a forecast. */
    public boolean isApplicable(PeriodDto period) {
        return period != null
                && period.periodType() == PeriodType.PAY_CYCLE
                && Boolean.TRUE.equals(period.salaryWallet())
                && (period.cycleState() == CycleState.OPEN || period.cycleState() == CycleState.AWAITING_SALARY);
    }

    /**
     * @param period    the resolved period the consumer is showing
     * @param asOfDate  the viewing day; {@code null} → today
     * @param tile      the Stage 3 tile already computed for {@code period} / {@code asOfDate} by the caller, or
     *                  {@code null} to load it here through the same {@link FixedPaymentDashboardService} path
     * @return the forecast, or {@code null} when {@code period} is not applicable
     */
    public ForecastV2Dto forecast(Wallet wallet, UUID userId, PeriodDto period, LocalDate asOfDate,
                                  FixedTransactionsTileDto tile) {
        if (!isApplicable(period)) {
            return null;
        }
        LocalDate today = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        FixedTransactionsTileDto committedSource = tile != null
                ? tile
                : fixedPaymentDashboardService.getFixedPaymentsTileData(period, wallet, userId, today);
        List<FixedOccurrenceRowDto> rows = tileRows(committedSource);
        List<CommittedOccurrenceDto> committedRows = rows.stream()
                .filter(row -> CommittedOccurrence.from(row).committed())
                .sorted(Comparator.comparing(FixedOccurrenceRowDto::dueDate)
                        .thenComparing(FixedOccurrenceRowDto::occurrenceId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CommittedOccurrenceDto::from)
                .toList();

        if (period.cycleState() == CycleState.AWAITING_SALARY) {
            return awaitingSalary(wallet.getBalance(), committedRows);
        }
        return openCycle(wallet, userId, period, today, rows, committedRows);
    }

    private ForecastV2Dto openCycle(Wallet wallet, UUID userId, PeriodDto period, LocalDate today,
                                    List<FixedOccurrenceRowDto> rows, List<CommittedOccurrenceDto> committedRows) {
        LocalDate start = period.startDate();
        LocalDate end = period.endDate();
        if (start.isAfter(today)) {
            throw new IllegalArgumentException("period must not be in the future");
        }
        // an OPEN cycle ends the day before the expected payday, so today never lies beyond it — the cap only
        // protects an explicit asOfDate past the end (R = 0 then, no remaining horizon)
        LocalDate toDate = today.isBefore(end) ? today : end;
        int dayIndex = InclusiveDateRange.daysBetween(start, toDate);
        int cycleLengthDays = InclusiveDateRange.daysBetween(start, end);
        int daysRemaining = Math.max(0, InclusiveDateRange.daysBetween(toDate.plusDays(1), end));

        // the ONE pace-eligible series of this computation: pace, base forecast and one-off impacts all read it
        List<DailyTotal> daily = transactionQueryService.dailyVariableTotals(wallet.getId(), userId, start, toDate);
        CurrentPace pace = calculator.currentPace(daily, daysRemaining);

        PayCycle currentCycle = PayCycle.open(userId, wallet.getId(), start, end.plusDays(1));
        HistoricalBaseline baseline = historicalVariableSpendService.baseline(
                userId, wallet.getId(), currentCycle, dayIndex, daysRemaining);

        List<CommittedOccurrence> occurrences = rows.stream().map(CommittedOccurrence::from).toList();
        ForecastInput input = new ForecastInput(wallet.getBalance(), toDate, dayIndex, cycleLengthDays,
                daysRemaining, occurrences, pace, baseline);
        ForecastV2 forecast = calculator.forecast(input);

        // gross candidates (excluded rows included) — the explanation layer; the impacts recompute over `daily`
        List<VariableExpense> candidates = transactionQueryService.unlinkedExpenses(
                wallet.getId(), userId, start, toDate);
        List<OneOff> oneOffs = oneOffDetector.detect(candidates, daily, input);

        return new ForecastV2Dto(
                forecast.status(),
                forecast.confidence(),
                forecast.discretionaryNow(),
                forecast.discretionaryPerDay(),
                forecast.committed(),
                forecast.expectedVariableRemaining(),
                forecast.pessimisticVariableRemaining(),
                forecast.optimisticVariableRemaining(),
                forecast.expectedEndBalanceTypical(),
                forecast.expectedEndBalanceLow(),
                forecast.expectedEndBalanceHigh(),
                pace.trimmedDailyPace(),
                pace.rawDailyBurnRate(),
                baseline.typicalPerDay(),
                forecast.historyWeight(),
                baseline.cyclesUsed(),
                baseline.perCycle().stream().map(BaselineCycleDto::from).toList(),
                oneOffs.stream().map(OneOffDto::from).toList(),
                committedRows,
                null,
                pace.variableToDate(),
                baseline.median(),
                pace.paceProjection(),
                // Stage 5.4: the same `daily` list and baseline cycles, re-shaped as cumulative series
                ForecastTrajectoryBuilder.build(start, cycleLengthDays, daily, baseline.perCycle()));
    }

    /**
     * The Stage 2 / 4.5 awaiting-salary contract: the expected payday has passed without a salary, so there is
     * no remaining horizon to project over — no pace, no blend, no range, no verdict. What is still a fact is the
     * obligation side: the committed occurrences of the window (through today, as the Stage 3 tile extends it)
     * and what the balance leaves after them.
     */
    private ForecastV2Dto awaitingSalary(BigDecimal walletBalance, List<CommittedOccurrenceDto> committedRows) {
        BigDecimal committed = money(committedRows.stream()
                .map(CommittedOccurrenceDto::expectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal discretionaryNow = money(walletBalance).subtract(committed);
        return new ForecastV2Dto(
                null, null,
                discretionaryNow, null,
                committed,
                null, null, null,
                null, null, null,
                null, null, null, null, null,
                List.of(), List.of(),
                committedRows,
                SpendingProjectionCalculator.REASON_AWAITING_SALARY);
    }

    /** Every row the tile classified, whatever its bucket; the calculator keeps only the committed ones. */
    private static List<FixedOccurrenceRowDto> tileRows(FixedTransactionsTileDto tile) {
        List<FixedOccurrenceRowDto> rows = new ArrayList<>();
        rows.addAll(nullSafe(tile.overdue()));
        rows.addAll(nullSafe(tile.upcoming()));
        rows.addAll(nullSafe(tile.paid()));
        rows.addAll(nullSafe(tile.afterPayday()));
        return rows;
    }

    private static List<FixedOccurrenceRowDto> nullSafe(List<FixedOccurrenceRowDto> rows) {
        return rows == null ? List.of() : rows;
    }
}
