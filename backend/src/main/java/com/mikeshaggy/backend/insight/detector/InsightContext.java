package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonCategoryDto;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService.CategoryDayBaseline;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleVariableTotal;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.UnlinkedExpense;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.CycleMinimum;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The inputs of one evaluation (Stage 7.2): the salary wallet's current pay cycle plus the canonical financial
 * read models the detectors build on. The cycle, wallet and day are resolved up front; every other input is
 * loaded <em>lazily</em> through the existing service that owns it — on the first accessor call, at most once
 * per evaluation (a failure is memoised and rethrown too), and not at all when no detector asks for it.
 * <p>
 * Absent data is never turned into zero: {@link #forecast()} is {@link Optional#empty()} when no Forecast v2
 * exists for this cycle (or the {@code forecast-v2} flag is off); the other accessors return what their source
 * returned (possibly empty lists) or throw when the source fails.
 */
public final class InsightContext {

    /** The authorised wallet as read at the start of the evaluation (no lazy entity state). */
    public record WalletSnapshot(Integer id, String name, BigDecimal balance) {
    }

    /** Cycle-to-date pace-eligible variable spend and its {@code SHOULDNT_HAVE} part (always {@code ≤ eligibleVariable}). */
    public record ImpulseSplit(BigDecimal eligibleVariable, BigDecimal shouldntHave) {
    }

    /** One fixed-payment occurrence as stored: {@code paidAmount} is the linked transaction's amount (Stage 3.3). */
    public record OccurrenceSnapshot(Long id, LocalDate dueDate, OccurrenceStatus status, BigDecimal expectedAmount,
                                     BigDecimal paidAmount) {
    }

    /** An active fixed payment of the wallet with its configured amount and its occurrences, by due date ascending. */
    public record FixedPaymentHistory(Integer id, String title, BigDecimal amount, Integer categoryId,
                                      List<OccurrenceSnapshot> occurrences) {

        public FixedPaymentHistory {
            occurrences = List.copyOf(occurrences);
        }
    }

    /**
     * One supplier per lazily loaded input; built by {@code InsightContextFactory}, stubbed directly in tests. The
     * Stage 7.2 five-input constructor leaves every Stage 7.3 input unavailable (its accessor throws, which skips the
     * detector that asks — never a silent zero).
     */
    public record Sources(
            Supplier<Optional<ForecastV2Dto>> forecast,
            Supplier<List<CycleComparisonCategoryDto>> categoryBaselines,
            Supplier<TransferSummaryResponse> transferSummary,
            Supplier<List<CycleMinimum>> sparklineMinima,
            Supplier<List<FixedOccurrenceRowDto>> fixedPaymentRows,
            Supplier<BalanceSparklineResponse> sparkline,
            Supplier<CategoryDayBaseline> categoryDayBaseline,
            Supplier<List<CycleVariableTotal>> cycleHistory,
            Supplier<Optional<WalletSnapshot>> savingsWallet,
            Supplier<ImpulseSplit> impulseSplit,
            Supplier<List<FixedPaymentHistory>> fixedPayments,
            Supplier<List<UnlinkedExpense>> expenseHistory) {

        public Sources {
            Objects.requireNonNull(forecast, "forecast");
            Objects.requireNonNull(categoryBaselines, "categoryBaselines");
            Objects.requireNonNull(transferSummary, "transferSummary");
            Objects.requireNonNull(sparklineMinima, "sparklineMinima");
            Objects.requireNonNull(fixedPaymentRows, "fixedPaymentRows");
            Objects.requireNonNull(sparkline, "sparkline");
            Objects.requireNonNull(categoryDayBaseline, "categoryDayBaseline");
            Objects.requireNonNull(cycleHistory, "cycleHistory");
            Objects.requireNonNull(savingsWallet, "savingsWallet");
            Objects.requireNonNull(impulseSplit, "impulseSplit");
            Objects.requireNonNull(fixedPayments, "fixedPayments");
            Objects.requireNonNull(expenseHistory, "expenseHistory");
        }

        public Sources(Supplier<Optional<ForecastV2Dto>> forecast,
                       Supplier<List<CycleComparisonCategoryDto>> categoryBaselines,
                       Supplier<TransferSummaryResponse> transferSummary,
                       Supplier<List<CycleMinimum>> sparklineMinima,
                       Supplier<List<FixedOccurrenceRowDto>> fixedPaymentRows) {
            this(forecast, categoryBaselines, transferSummary, sparklineMinima, fixedPaymentRows,
                    unavailable("sparkline"), unavailable("categoryDayBaseline"), unavailable("cycleHistory"),
                    unavailable("savingsWallet"), unavailable("impulseSplit"), unavailable("fixedPayments"),
                    unavailable("expenseHistory"));
        }

        private static <T> Supplier<T> unavailable(String input) {
            return () -> {
                throw new IllegalStateException("insight input not provided: " + input);
            };
        }
    }

    private final UUID userId;
    private final WalletSnapshot wallet;
    private final PayCycle cycle;
    private final LocalDate today;
    private final Lazy<Optional<ForecastV2Dto>> forecast;
    private final Lazy<List<CycleComparisonCategoryDto>> categoryBaselines;
    private final Lazy<TransferSummaryResponse> transferSummary;
    private final Lazy<List<CycleMinimum>> sparklineMinima;
    private final Lazy<List<FixedOccurrenceRowDto>> fixedPaymentRows;
    private final Lazy<BalanceSparklineResponse> sparkline;
    private final Lazy<CategoryDayBaseline> categoryDayBaseline;
    private final Lazy<List<CycleVariableTotal>> cycleHistory;
    private final Lazy<Optional<WalletSnapshot>> savingsWallet;
    private final Lazy<ImpulseSplit> impulseSplit;
    private final Lazy<List<FixedPaymentHistory>> fixedPayments;
    private final Lazy<List<UnlinkedExpense>> expenseHistory;

    public InsightContext(UUID userId, WalletSnapshot wallet, PayCycle cycle, LocalDate today, Sources sources) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
        this.cycle = Objects.requireNonNull(cycle, "cycle");
        this.today = Objects.requireNonNull(today, "today");
        Objects.requireNonNull(sources, "sources");
        this.forecast = new Lazy<>(sources.forecast());
        this.categoryBaselines = new Lazy<>(sources.categoryBaselines());
        this.transferSummary = new Lazy<>(sources.transferSummary());
        this.sparklineMinima = new Lazy<>(sources.sparklineMinima());
        this.fixedPaymentRows = new Lazy<>(sources.fixedPaymentRows());
        this.sparkline = new Lazy<>(sources.sparkline());
        this.categoryDayBaseline = new Lazy<>(sources.categoryDayBaseline());
        this.cycleHistory = new Lazy<>(sources.cycleHistory());
        this.savingsWallet = new Lazy<>(sources.savingsWallet());
        this.impulseSplit = new Lazy<>(sources.impulseSplit());
        this.fixedPayments = new Lazy<>(sources.fixedPayments());
        this.expenseHistory = new Lazy<>(sources.expenseHistory());
    }

    public UUID userId() {
        return userId;
    }

    public WalletSnapshot wallet() {
        return wallet;
    }

    /** The salary wallet's current cycle ({@code OPEN} or {@code AWAITING_SALARY}); its start keys every insight. */
    public PayCycle cycle() {
        return cycle;
    }

    public LocalDate today() {
        return today;
    }

    /** Forecast v2 of this cycle ({@code ForecastService}); empty when none exists or {@code forecast-v2} is off. */
    public Optional<ForecastV2Dto> forecast() {
        Optional<ForecastV2Dto> value = forecast.get();
        return value == null ? Optional.empty() : value;
    }

    /**
     * Per-category current vs baseline rows ({@code CycleComparisonService}, up to 6 baseline cycles). Its baseline is an
     * <em>average</em>, so no Stage 7.3 detector reads it — {@code CategoryAboveTypical} uses the median of
     * {@link #categoryDayBaseline()} (decision I2).
     */
    public List<CycleComparisonCategoryDto> categoryBaselines() {
        return categoryBaselines.get();
    }

    /** Stage 6.2 transfers summary of the wallet ({@code TransferSummaryService}). */
    public TransferSummaryResponse transferSummary() {
        return transferSummary.get();
    }

    /** Stage 6.1 lowest daily closing balance per closed cycle, oldest first ({@code BalanceSparklineService}). */
    public List<CycleMinimum> sparklineMinima() {
        return sparklineMinima.get();
    }

    /**
     * Every fixed-payment occurrence row of the Stage 3 tile for this cycle — the same tile the dashboard and the
     * forecast read (overdue, upcoming, paid; no after-payday horizon is requested, so none of those rows).
     */
    public List<FixedOccurrenceRowDto> fixedPaymentRows() {
        return fixedPaymentRows.get();
    }

    /**
     * The whole Stage 6.1 sparkline of the wallet (daily ledger closing balances of the last 90 days, paydays, closed
     * cycle minima) — {@link #sparklineMinima()} is part of the same load.
     */
    public BalanceSparklineResponse sparkline() {
        return sparkline.get();
    }

    /**
     * Per-category pace-eligible spend at today's cycle day against the median of the last 6 closed cycles at the same
     * day ({@code HistoricalCategorySpendService}).
     */
    public CategoryDayBaseline categoryDayBaseline() {
        return categoryDayBaseline.get();
    }

    /**
     * Whole-cycle pace-eligible variable spend of the most recent closed cycles, newest first — up to 7: the cycle that
     * closed last and the 6 before it ({@code HistoricalVariableSpendService#closedCycleTotals}).
     */
    public List<CycleVariableTotal> cycleHistory() {
        return cycleHistory.get();
    }

    /** The user's savings wallet ({@code SavingsWallets#single}); empty when there is none or more than one. */
    public Optional<WalletSnapshot> savingsWallet() {
        Optional<WalletSnapshot> value = savingsWallet.get();
        return value == null ? Optional.empty() : value;
    }

    /** Pace-eligible variable spend from the cycle start through today and its {@code SHOULDNT_HAVE} part. */
    public ImpulseSplit impulseSplit() {
        return impulseSplit.get();
    }

    /** The wallet's active fixed payments with their recent occurrences (about two years back, two months ahead). */
    public List<FixedPaymentHistory> fixedPayments() {
        return fixedPayments.get();
    }

    /** Unlinked expenses of the last 210 days through today, exclusion flags not applied, by date then id. */
    public List<UnlinkedExpense> expenseHistory() {
        return expenseHistory.get();
    }

    /** Memoising supplier: the first call's value — or its exception — is what every later call sees. */
    static final class Lazy<T> implements Supplier<T> {

        private final Supplier<T> source;
        private boolean resolved;
        private T value;
        private RuntimeException failure;

        Lazy(Supplier<T> source) {
            this.source = source;
        }

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
    }
}
