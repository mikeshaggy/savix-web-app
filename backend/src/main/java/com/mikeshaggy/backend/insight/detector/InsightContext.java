package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonCategoryDto;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
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

    /** One supplier per lazily loaded input; built by {@code InsightContextFactory}, stubbed directly in tests. */
    public record Sources(
            Supplier<Optional<ForecastV2Dto>> forecast,
            Supplier<List<CycleComparisonCategoryDto>> categoryBaselines,
            Supplier<TransferSummaryResponse> transferSummary,
            Supplier<List<CycleMinimum>> sparklineMinima,
            Supplier<List<FixedOccurrenceRowDto>> fixedPaymentRows) {

        public Sources {
            Objects.requireNonNull(forecast, "forecast");
            Objects.requireNonNull(categoryBaselines, "categoryBaselines");
            Objects.requireNonNull(transferSummary, "transferSummary");
            Objects.requireNonNull(sparklineMinima, "sparklineMinima");
            Objects.requireNonNull(fixedPaymentRows, "fixedPaymentRows");
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

    /** Per-category current vs baseline rows ({@code CycleComparisonService}, up to 6 baseline cycles). */
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
