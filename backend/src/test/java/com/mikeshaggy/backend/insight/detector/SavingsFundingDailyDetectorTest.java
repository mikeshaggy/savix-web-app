package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleVariableTotal;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.insight.detector.InsightContext.WalletSnapshot;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.regression.September2026Fixture;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWalletFlow;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CycleSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.mikeshaggy.backend.insight.detector.TestContexts.SAVINGS;
import static com.mikeshaggy.backend.insight.detector.TestContexts.USER;
import static com.mikeshaggy.backend.insight.detector.TestContexts.WALLET;
import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static com.mikeshaggy.backend.insight.detector.TestContexts.money;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 7.3 {@code SavingsFundingDaily}: on the audited data about 11.4k came in from savings against 8.4k going out,
 * and savings hold 460.01 — far below one typical cycle of variable spending.
 */
class SavingsFundingDailyDetectorTest {

    private static final WalletSnapshot SAVINGS_WALLET = new WalletSnapshot(SAVINGS, "FX_SAVINGS_WALLET",
            September2026Fixture.SAVINGS_WALLET_BALANCE);

    private final SavingsFundingDailyDetector detector = new SavingsFundingDailyDetector();

    @Test
    void audited_11_4kIn_vs_8_4kOut_withSavingsAt460_01_fires() {
        // last two closed cycles: in 6,100.00 / out 4,300.00 and in 5,251.00 / out 4,050.00 → net +3,001.00
        TransferSummaryResponse summary = summary(cycle("2026-08-10", "2026-09-08", "6100.00", "4300.00"),
                cycle("2026-07-10", "2026-08-09", "5251.00", "4050.00"),
                cycle("2026-06-10", "2026-07-09", "0.00", "9000.00"));

        InsightCandidate candidate = detector.detect(context().savingsWallet(SAVINGS_WALLET).transferSummary(summary)
                .cycleHistory(history("3600.10", "3420.00", "3810.55", "2950.00", "4100.00", "3333.33")).build())
                .orElseThrow();

        assertThat(candidate.severity()).isEqualTo(InsightSeverity.WARN);
        assertThat(candidate.dedupeKey()).isEqualTo("savings:2");
        assertThat(candidate.actionType()).isEqualTo(InsightActionType.LINK);
        assertThat(candidate.actionRef()).isEqualTo("/wallets/transfers");
        assertThat(candidate.payload())
                .containsEntry("netInflow", money("3001.00"))
                .containsEntry("savingsBalance", money("460.01"))
                .containsEntry("typicalCycleVariable", money("3510.05"))
                .containsEntry("typicalCycles", 6);
    }

    @Test
    void onlyTheLastTwoClosedCyclesCount() {
        // the third cycle's large outflow to savings is outside the window
        assertThat(detect(cycle("2026-08-10", "2026-09-08", "100.00", "0.00"),
                cycle("2026-07-10", "2026-08-09", "0.00", "0.00"),
                cycle("2026-06-10", "2026-07-09", "0.00", "50000.00"))).isTrue();
    }

    @Test
    void netFlowMustBeStrictlyPositive() {
        assertThat(detect(cycle("2026-08-10", "2026-09-08", "500.00", "500.00"),
                cycle("2026-07-10", "2026-08-09", "200.00", "200.00"))).isFalse();
        assertThat(detect(cycle("2026-08-10", "2026-09-08", "500.01", "500.00"),
                cycle("2026-07-10", "2026-08-09", "200.00", "200.00"))).isTrue();
        assertThat(detect(cycle("2026-08-10", "2026-09-08", "900.00", "0.00"),
                cycle("2026-07-10", "2026-08-09", "0.00", "1000.00"))).isFalse();
    }

    @Test
    void savingsBalanceAtOrAboveTheTypicalCycle_doesNotFire() {
        TransferSummaryResponse summary = summary(cycle("2026-08-10", "2026-09-08", "900.00", "0.00"),
                cycle("2026-07-10", "2026-08-09", "0.00", "0.00"));
        List<CycleVariableTotal> history = history("1000.00", "1000.00", "1000.00");

        assertThat(detector.detect(context().savingsWallet(savings("1000.00")).transferSummary(summary)
                .cycleHistory(history).build())).isEmpty();
        assertThat(detector.detect(context().savingsWallet(savings("999.99")).transferSummary(summary)
                .cycleHistory(history).build())).isPresent();
    }

    @Test
    void transfersWithOtherWallets_areIgnored() {
        CycleSummary august = new CycleSummary(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-09-08"),
                CycleState.CLOSED, money("5000.00"), money("0.00"), money("5000.00"),
                List.of(new CounterWalletFlow(9, "Other", money("5000.00"), money("0.00"), money("5000.00"))));
        TransferSummaryResponse summary = summary(august, cycle("2026-07-10", "2026-08-09", "0.00", "0.00"));

        assertThat(detector.detect(context().savingsWallet(SAVINGS_WALLET).transferSummary(summary)
                .cycleHistory(history("3000.00")).build())).isEmpty();
    }

    @Test
    void missingInputs_meanNoInsight_neverZero() {
        TransferSummaryResponse twoCycles = summary(cycle("2026-08-10", "2026-09-08", "900.00", "0.00"),
                cycle("2026-07-10", "2026-08-09", "0.00", "0.00"));
        // no single savings wallet: the transfer summary is not even read
        assertThat(detector.detect(context().savingsWallet(null).build())).isEmpty();
        // only one closed cycle
        assertThat(detector.detect(context().savingsWallet(SAVINGS_WALLET)
                .transferSummary(summary(cycle("2026-08-10", "2026-09-08", "900.00", "0.00"))).build())).isEmpty();
        // no closed-cycle variable history: not a typical of zero
        assertThat(detector.detect(context().savingsWallet(SAVINGS_WALLET).transferSummary(twoCycles)
                .cycleHistory(List.of()).build())).isEmpty();
    }

    @Test
    void stableKey_andCandidate_acrossEvaluations() {
        TransferSummaryResponse summary = summary(cycle("2026-08-10", "2026-09-08", "900.00", "0.00"),
                cycle("2026-07-10", "2026-08-09", "0.00", "0.00"));
        var first = detector.detect(context().savingsWallet(SAVINGS_WALLET).transferSummary(summary)
                .cycleHistory(history("3000.00")).build());
        var second = detector.detect(context().savingsWallet(SAVINGS_WALLET).transferSummary(summary)
                .cycleHistory(history("3000.00")).build());
        assertThat(first).isEqualTo(second);
    }

    private boolean detect(CycleSummary... cycles) {
        return detector.detect(context().savingsWallet(SAVINGS_WALLET).transferSummary(summary(cycles))
                .cycleHistory(history("3000.00")).build()).isPresent();
    }

    private static WalletSnapshot savings(String balance) {
        return new WalletSnapshot(SAVINGS, "FX_SAVINGS_WALLET", new BigDecimal(balance));
    }

    private static TransferSummaryResponse summary(CycleSummary... closed) {
        return new TransferSummaryResponse(WALLET, null, List.of(closed), List.of());
    }

    private static CycleSummary cycle(String start, String end, String in, String out) {
        BigDecimal net = money(in).subtract(money(out));
        return new CycleSummary(LocalDate.parse(start), LocalDate.parse(end), CycleState.CLOSED, money(in), money(out),
                net, List.of(new CounterWalletFlow(SAVINGS, "FX_SAVINGS_WALLET", money(in), money(out), net)));
    }

    private static List<CycleVariableTotal> history(String... totals) {
        List<CycleVariableTotal> history = new ArrayList<>();
        LocalDate end = LocalDate.parse("2026-09-08");
        for (String total : totals) {
            history.add(new CycleVariableTotal(PayCycle.closed(USER, WALLET, end.minusDays(29), end), money(total)));
            end = end.minusDays(30);
        }
        return history;
    }
}
