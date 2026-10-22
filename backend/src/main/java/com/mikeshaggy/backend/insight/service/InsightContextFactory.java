package com.mikeshaggy.backend.insight.service;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonService;
import com.mikeshaggy.backend.analytics.forecast.ForecastService;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.insight.detector.InsightContext;
import com.mikeshaggy.backend.insight.detector.InsightContext.ImpulseSplit;
import com.mikeshaggy.backend.insight.detector.InsightContext.Sources;
import com.mikeshaggy.backend.insight.detector.InsightContext.WalletSnapshot;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse;
import com.mikeshaggy.backend.ledger.service.BalanceSparklineService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transfer.service.TransferSummaryService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Wires {@link InsightContext} to the services that own each input — no financial figure is recomputed here.
 * <p>
 * Query reuse: the Stage 3 fixed-payment tile is loaded at most once per evaluation and shared by
 * {@code fixedPaymentRows()} and {@code forecast()} (handed to {@link ForecastService}, which would otherwise load
 * it again). The insight package depends on these services, never the other way round, so the planned
 * {@code DashboardSummaryService → InsightService} call (Stage 7.4) introduces no cycle.
 * <p>
 * Stage 7.3 inputs follow the same rule: the sparkline is loaded once for both {@code sparkline()} and
 * {@code sparklineMinima()}; cycle history, category baseline and impulse split each read their canonical
 * pace-eligible query once; fixed payments and the savings wallet come from {@link InsightInputQueries} (each read in
 * its own read-only transaction, mapped to snapshots — no lazy entity state escapes into detectors). Every input is
 * read through a transactional service, so detection holds at most one connection at a time.
 */
@Component
@RequiredArgsConstructor
public class InsightContextFactory {

    /** Category baselines span the same number of cycles as the Stage 4 history (N ≤ 6). */
    static final int BASELINE_CYCLES = 6;
    /** Unlinked-expense lookback of {@code RecurringMerchantDetected}: room for 3+ monthly charges plus slack. */
    static final int EXPENSE_HISTORY_DAYS = 210;

    private final ForecastService forecastService;
    private final FixedPaymentDashboardService fixedPaymentDashboardService;
    private final CycleComparisonService cycleComparisonService;
    private final TransferSummaryService transferSummaryService;
    private final BalanceSparklineService balanceSparklineService;
    private final HistoricalCategorySpendService historicalCategorySpendService;
    private final HistoricalVariableSpendService historicalVariableSpendService;
    private final AnalyticsTransactionQueryService transactionQueryService;
    private final InsightInputQueries inputQueries;
    private final FeatureFlags featureFlags;

    /**
     * @param period the pay-cycle-v2 {@code PAY_CYCLE} period of {@code cycle}, already resolved and checked by the
     *               caller
     */
    public InsightContext create(UUID userId, Wallet wallet, PayCycle cycle, PeriodDto period, LocalDate today) {
        Integer walletId = wallet.getId();
        Supplier<FixedTransactionsTileDto> tile = memoise(
                () -> fixedPaymentDashboardService.getFixedPaymentsTileData(period, wallet, userId, today));
        Supplier<BalanceSparklineResponse> sparkline = memoise(
                () -> balanceSparklineService.getSparkline(walletId, userId, BalanceSparklineService.DEFAULT_DAYS));

        Sources sources = new Sources(
                () -> forecast(wallet, userId, period, today, tile),
                () -> cycleComparisonService.getCycleComparison(walletId, userId, today, BASELINE_CYCLES,
                        null, CategoryAggregationMode.ALL, null).categories(),
                () -> transferSummaryService.getSummary(walletId, userId, TransferSummaryService.DEFAULT_CYCLES),
                () -> sparkline.get().cycleMinima(),
                () -> rows(tile.get()),
                sparkline,
                () -> historicalCategorySpendService.atDay(userId, walletId, cycle, today),
                () -> historicalVariableSpendService.closedCycleTotals(userId, walletId,
                        HistoricalVariableSpendService.MAX_TOTAL_CYCLES),
                () -> inputQueries.savingsWallet(userId, walletId),
                () -> new ImpulseSplit(
                        transactionQueryService.sumUnlinkedForPace(walletId, userId, cycle.start(), today),
                        transactionQueryService.sumUnlinkedForPaceByImportance(walletId, userId, cycle.start(), today,
                                Importance.SHOULDNT_HAVE)),
                () -> inputQueries.fixedPayments(walletId, userId, today),
                () -> transactionQueryService.unlinkedExpenseRows(walletId, userId,
                        today.minusDays(EXPENSE_HISTORY_DAYS - 1L), today));
        return new InsightContext(userId, new WalletSnapshot(walletId, wallet.getName(), wallet.getBalance()),
                cycle, today, sources);
    }

    /**
     * Forecast v2 presents Forecast v2 semantics, which are gated by {@code forecast-v2} (plan §8) — with that flag
     * off, detectors see no forecast rather than one the rest of the product does not show.
     */
    private Optional<ForecastV2Dto> forecast(Wallet wallet, UUID userId, PeriodDto period, LocalDate today,
                                             Supplier<FixedTransactionsTileDto> tile) {
        if (!featureFlags.forecastV2() || !forecastService.isApplicable(period)) {
            return Optional.empty();
        }
        return Optional.ofNullable(forecastService.forecast(wallet, userId, period, today, tile.get()));
    }

    private static List<FixedOccurrenceRowDto> rows(FixedTransactionsTileDto tile) {
        List<FixedOccurrenceRowDto> rows = new ArrayList<>();
        addAll(rows, tile.overdue());
        addAll(rows, tile.upcoming());
        addAll(rows, tile.paid());
        addAll(rows, tile.afterPayday());
        return List.copyOf(rows);
    }

    private static void addAll(List<FixedOccurrenceRowDto> target, List<FixedOccurrenceRowDto> rows) {
        if (rows != null) {
            target.addAll(rows);
        }
    }

    /** Value or failure of the first call, replayed afterwards (same rule as the context's own inputs). */
    private static <T> Supplier<T> memoise(Supplier<T> source) {
        return new Supplier<>() {
            private boolean resolved;
            private T value;
            private RuntimeException failure;

            @Override
            public synchronized T get() {
                if (!resolved) {
                    try {
                        value = source.get();
                    } catch (RuntimeException e) {
                        failure = e;
                    }
                    resolved = true;
                }
                if (failure != null) {
                    throw failure;
                }
                return value;
            }
        };
    }
}
