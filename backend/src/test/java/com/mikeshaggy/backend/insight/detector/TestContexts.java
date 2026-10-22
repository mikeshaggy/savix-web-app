package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonCategoryDto;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.analytics.forecast.HistoricalCategorySpendService.CategoryDayBaseline;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleVariableTotal;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.UnlinkedExpense;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.insight.detector.InsightContext.FixedPaymentHistory;
import com.mikeshaggy.backend.insight.detector.InsightContext.ImpulseSplit;
import com.mikeshaggy.backend.insight.detector.InsightContext.Sources;
import com.mikeshaggy.backend.insight.detector.InsightContext.WalletSnapshot;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.CycleMinimum;
import com.mikeshaggy.backend.regression.September2026Fixture;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Builds {@link InsightContext}s for detector unit tests. Every input not set explicitly is <em>unavailable</em>: its
 * accessor throws, so a test also proves that a detector reads only the inputs it needs and never treats a missing
 * input as zero.
 */
final class TestContexts {

    static final UUID USER = September2026Fixture.USER_ID;
    static final Integer WALLET = September2026Fixture.SALARY_WALLET_ID;
    static final Integer SAVINGS = September2026Fixture.SAVINGS_WALLET_ID;
    /** The audited current cycle: Sep 9 → Oct 8, next salary expected Oct 9, OPEN. */
    static final PayCycle SEPTEMBER = PayCycle.open(USER, WALLET, September2026Fixture.CURRENT_CYCLE_START,
            September2026Fixture.EXPECTED_NEXT_PAYDAY);
    static final PayCycle AUGUST = PayCycle.closed(USER, WALLET, September2026Fixture.LAST_CYCLE_START,
            September2026Fixture.LAST_CYCLE_END);

    private PayCycle cycle = SEPTEMBER;
    private LocalDate today = September2026Fixture.TODAY;
    private BigDecimal balance = September2026Fixture.SALARY_WALLET_BALANCE;
    private Supplier<Optional<ForecastV2Dto>> forecast = unavailable("forecast");
    private Supplier<TransferSummaryResponse> transferSummary = unavailable("transferSummary");
    private Supplier<List<CycleMinimum>> sparklineMinima = unavailable("sparklineMinima");
    private Supplier<BalanceSparklineResponse> sparkline = unavailable("sparkline");
    private Supplier<CategoryDayBaseline> categoryDayBaseline = unavailable("categoryDayBaseline");
    private Supplier<List<CycleVariableTotal>> cycleHistory = unavailable("cycleHistory");
    private Supplier<Optional<WalletSnapshot>> savingsWallet = unavailable("savingsWallet");
    private Supplier<ImpulseSplit> impulseSplit = unavailable("impulseSplit");
    private Supplier<List<FixedPaymentHistory>> fixedPayments = unavailable("fixedPayments");
    private Supplier<List<UnlinkedExpense>> expenseHistory = unavailable("expenseHistory");

    static TestContexts context() {
        return new TestContexts();
    }

    TestContexts cycle(PayCycle value) {
        cycle = value;
        return this;
    }

    TestContexts today(LocalDate value) {
        today = value;
        return this;
    }

    TestContexts forecast(ForecastV2Dto value) {
        forecast = () -> Optional.ofNullable(value);
        return this;
    }

    TestContexts transferSummary(TransferSummaryResponse value) {
        transferSummary = () -> value;
        return this;
    }

    TestContexts sparklineMinima(List<CycleMinimum> value) {
        sparklineMinima = () -> value;
        return this;
    }

    TestContexts sparkline(BalanceSparklineResponse value) {
        sparkline = () -> value;
        return this;
    }

    TestContexts categoryDayBaseline(CategoryDayBaseline value) {
        categoryDayBaseline = () -> value;
        return this;
    }

    TestContexts cycleHistory(List<CycleVariableTotal> value) {
        cycleHistory = () -> value;
        return this;
    }

    TestContexts savingsWallet(WalletSnapshot value) {
        savingsWallet = () -> Optional.ofNullable(value);
        return this;
    }

    TestContexts impulseSplit(ImpulseSplit value) {
        impulseSplit = () -> value;
        return this;
    }

    TestContexts fixedPayments(List<FixedPaymentHistory> value) {
        fixedPayments = () -> value;
        return this;
    }

    TestContexts expenseHistory(List<UnlinkedExpense> value) {
        expenseHistory = () -> value;
        return this;
    }

    InsightContext build() {
        Supplier<List<CycleComparisonCategoryDto>> categoryBaselines = unavailable("categoryBaselines");
        Supplier<List<FixedOccurrenceRowDto>> fixedPaymentRows = unavailable("fixedPaymentRows");
        return new InsightContext(USER, new WalletSnapshot(WALLET, September2026Fixture.SALARY_WALLET_NAME, balance),
                cycle, today, new Sources(forecast, categoryBaselines, transferSummary, sparklineMinima,
                fixedPaymentRows, sparkline, categoryDayBaseline, cycleHistory, savingsWallet, impulseSplit,
                fixedPayments, expenseHistory));
    }

    static <T> Supplier<T> unavailable(String input) {
        return () -> {
            throw new IllegalStateException("test: input not provided: " + input);
        };
    }

    static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
