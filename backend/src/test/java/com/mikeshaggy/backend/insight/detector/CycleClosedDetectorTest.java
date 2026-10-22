package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleVariableTotal;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.insight.detector.InsightContext.WalletSnapshot;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.Point;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWalletFlow;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CycleSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.mikeshaggy.backend.insight.detector.TestContexts.AUGUST;
import static com.mikeshaggy.backend.insight.detector.TestContexts.SAVINGS;
import static com.mikeshaggy.backend.insight.detector.TestContexts.USER;
import static com.mikeshaggy.backend.insight.detector.TestContexts.WALLET;
import static com.mikeshaggy.backend.insight.detector.TestContexts.context;
import static com.mikeshaggy.backend.insight.detector.TestContexts.money;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 7.3 {@code CycleClosed}: the audited Aug 10 – Sep 8 cycle (variable 3,600.60 here, synthetic) summarised and
 * keyed by its own start — the persistence side (exactly once, also after rollover) is covered by
 * {@code InsightServiceTest} and the end-to-end detector tests.
 */
class CycleClosedDetectorTest {

    private static final WalletSnapshot SAVINGS_WALLET = new WalletSnapshot(SAVINGS, "FX_SAVINGS_WALLET", money("460.01"));

    private final CycleClosedDetector detector = new CycleClosedDetector();

    @Test
    void august10ToSeptember8_isSummarised_andKeyedByTheClosedCycleStart() {
        InsightCandidate candidate = detector.detect(context()
                .cycleHistory(history(AUGUST, "3600.60", "3420.00", "3810.55", "2950.00"))
                .sparkline(sparkline(new Point(AUGUST.end().minusDays(1), money("10.00")),
                        new Point(AUGUST.end(), money("-12.40"))))
                .savingsWallet(SAVINGS_WALLET)
                .transferSummary(transfers(AUGUST, "6100.00", "4300.00"))
                .build()).orElseThrow();

        assertThat(candidate.severity()).isEqualTo(InsightSeverity.INFO);
        assertThat(candidate.dedupeKey()).isEqualTo("cycle:2026-08-10");
        assertThat(candidate.title()).isEqualTo("Cycle 2026-08-10 – 2026-09-08 closed");
        assertThat(candidate.actionType()).isEqualTo(InsightActionType.LINK);
        assertThat(candidate.actionRef()).isEqualTo("/analytics/comparison");
        assertThat(candidate.payload())
                .containsEntry("cycleStart", "2026-08-10")
                .containsEntry("cycleEnd", "2026-09-08")
                .containsEntry("lengthDays", 30)
                .containsEntry("endBalance", money("-12.40"))
                .containsEntry("variableSpent", money("3600.60"))
                .containsEntry("typicalVariable", money("3420.00"))   // median of the 3 cycles before August
                .containsEntry("typicalCycles", 3)
                .containsEntry("netSavingsFlow", money("1800.00"));
        assertThat(candidate.description())
                .isEqualTo("Ended with -12.40 PLN left; variable spending 3600.60 PLN (typical 3420.00 PLN); "
                        + "net savings flow 1800.00 PLN.");
    }

    @Test
    void missingParts_areNull_neverZero() {
        InsightCandidate candidate = detector.detect(context()
                .cycleHistory(history(AUGUST, "3600.60"))
                .sparkline(sparkline())
                .savingsWallet(null)
                .build()).orElseThrow();

        assertThat(candidate.payload())
                .containsEntry("endBalance", null)
                .containsEntry("typicalVariable", null)
                .containsEntry("typicalCycles", 0)
                .containsEntry("netSavingsFlow", null);
        assertThat(candidate.description()).isEqualTo("Variable spending 3600.60 PLN.");
    }

    @Test
    void afterRollover_theNewlyClosedCycleIsReported_notTheOlderOne() {
        PayCycle september = PayCycle.closed(USER, WALLET, LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 8));
        PayCycle october = PayCycle.open(USER, WALLET, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 11, 10));

        InsightCandidate candidate = detector.detect(context().cycle(october).today(LocalDate.of(2026, 10, 10))
                .cycleHistory(history(september, "3300.00", "3600.60"))
                .sparkline(sparkline()).savingsWallet(null).build()).orElseThrow();

        assertThat(candidate.dedupeKey()).isEqualTo("cycle:2026-09-09");
        assertThat(candidate.payload()).containsEntry("typicalVariable", money("3600.60"));
    }

    @Test
    void noClosedCycle_orAGap_noInsight() {
        assertThat(detector.detect(context().cycleHistory(List.of()).build())).isEmpty();
        // the newest closed cycle does not end the day before the current one starts
        PayCycle july = PayCycle.closed(USER, WALLET, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9));
        assertThat(detector.detect(context().cycleHistory(history(july, "3000.00")).build())).isEmpty();
    }

    @Test
    void stableAcrossEvaluations() {
        var build = (java.util.function.Supplier<InsightContext>) () -> context()
                .cycleHistory(history(AUGUST, "3600.60", "3420.00")).sparkline(sparkline()).savingsWallet(null).build();
        assertThat(detector.detect(build.get())).isEqualTo(detector.detect(build.get()));
    }

    /** {@code newest} first, then one 30-day cycle before the other for each further total. */
    private static List<CycleVariableTotal> history(PayCycle newest, String... totals) {
        List<CycleVariableTotal> history = new ArrayList<>();
        history.add(new CycleVariableTotal(newest, money(totals[0])));
        LocalDate end = newest.start().minusDays(1);
        for (int i = 1; i < totals.length; i++) {
            history.add(new CycleVariableTotal(PayCycle.closed(USER, WALLET, end.minusDays(29), end), money(totals[i])));
            end = end.minusDays(30);
        }
        return history;
    }

    private static BalanceSparklineResponse sparkline(Point... points) {
        LocalDate today = LocalDate.of(2026, 9, 14);
        return new BalanceSparklineResponse(WALLET, today.minusDays(89), today, List.of(points), List.of(), List.of());
    }

    private static TransferSummaryResponse transfers(PayCycle cycle, String in, String out) {
        var net = money(in).subtract(money(out));
        CycleSummary summary = new CycleSummary(cycle.start(), cycle.end(), CycleState.CLOSED, money(in), money(out),
                net, List.of(new CounterWalletFlow(SAVINGS, "FX_SAVINGS_WALLET", money(in), money(out), net)));
        return new TransferSummaryResponse(WALLET, null, List.of(summary), List.of());
    }
}
