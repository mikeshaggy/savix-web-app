package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleVariableTotal;
import com.mikeshaggy.backend.common.calculation.Quantiles;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.insight.detector.InsightContext.WalletSnapshot;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.Point;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWalletFlow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.zero;
import static com.mikeshaggy.backend.insight.detector.InsightFormat.pln;

/**
 * The pay cycle that ended the day before the current one started, summarised once: dates, the ledger balance it
 * ended with, its whole-cycle pace-eligible variable spend against the median of the (up to 6) closed cycles before
 * it, and its net transfer flow with the savings wallet (salary-wallet side, + = from savings). Keyed by the closed
 * cycle's start and stored once per wallet ({@code ONCE_PER_WALLET}), so it is emitted exactly once per closed cycle —
 * also when the evaluation happens late in the next cycle or the anchors are re-read. Parts without data are
 * {@code null} in the payload and left out of the text (never zero).
 */
@Component
public class CycleClosedDetector implements InsightDetector {

    @Override
    public InsightType type() {
        return InsightType.CYCLE_CLOSED;
    }

    @Override
    public Optional<InsightCandidate> detect(InsightContext ctx) {
        List<CycleVariableTotal> history = ctx.cycleHistory();
        if (history.isEmpty()) {
            return Optional.empty();
        }
        CycleVariableTotal closed = history.getFirst();
        PayCycle cycle = closed.cycle();
        if (!cycle.end().plusDays(1).equals(ctx.cycle().start())) {
            return Optional.empty();
        }

        List<BigDecimal> before = history.subList(1, history.size()).stream()
                .limit(DetectorThresholds.SAVINGS_MEDIAN_CYCLES)
                .map(CycleVariableTotal::variableTotal)
                .toList();
        BigDecimal typical = before.isEmpty() ? null : Quantiles.median(before);
        BigDecimal endBalance = ctx.sparkline().points().stream()
                .filter(point -> point.date().equals(cycle.end()))
                .map(Point::balance)
                .findFirst()
                .orElse(null);
        Optional<WalletSnapshot> savings = ctx.savingsWallet();
        BigDecimal netSavingsFlow = savings.isEmpty() ? null : netSavingsFlow(ctx.transferSummary(), cycle,
                savings.get().id());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cycleStart", cycle.start().toString());
        payload.put("cycleEnd", cycle.end().toString());
        payload.put("lengthDays", cycle.lengthDays());
        payload.put("endBalance", endBalance);
        payload.put("variableSpent", closed.variableTotal());
        payload.put("typicalVariable", typical);
        payload.put("typicalCycles", before.size());
        payload.put("netSavingsFlow", netSavingsFlow);
        payload.put("savingsWalletName", savings.map(WalletSnapshot::name).orElse(null));

        List<String> parts = new ArrayList<>();
        if (endBalance != null) {
            parts.add("ended with " + pln(endBalance) + " left");
        }
        parts.add("variable spending " + pln(closed.variableTotal())
                + (typical == null ? "" : " (typical " + pln(typical) + ")"));
        if (netSavingsFlow != null) {
            parts.add("net savings flow " + pln(netSavingsFlow));
        }
        String description = String.join("; ", parts);
        description = Character.toUpperCase(description.charAt(0)) + description.substring(1) + ".";

        return Optional.of(new InsightCandidate(
                type(),
                InsightSeverity.INFO,
                "cycle:" + cycle.start(),
                "Cycle %s – %s closed".formatted(cycle.start(), cycle.end()),
                description,
                payload,
                InsightActionType.LINK,
                "/analytics/comparison"));
    }

    /** The closed cycle's net flow with savings; {@code null} when the summary does not list that cycle. */
    private static BigDecimal netSavingsFlow(TransferSummaryResponse summary, PayCycle cycle, Integer savingsWalletId) {
        if (summary == null) {
            return null;
        }
        return summary.cycles().stream()
                .filter(row -> row.start().equals(cycle.start()))
                .findFirst()
                .map(row -> row.counterWallets().stream()
                        .filter(flow -> savingsWalletId.equals(flow.walletId()))
                        .map(CounterWalletFlow::net)
                        .map(net -> money(net))
                        .findFirst()
                        .orElse(zero()))
                .orElse(null);
    }
}
