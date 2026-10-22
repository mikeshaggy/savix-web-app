package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleVariableTotal;
import com.mikeshaggy.backend.common.calculation.Quantiles;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.insight.detector.InsightContext.WalletSnapshot;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWalletFlow;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CycleSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.zero;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.SAVINGS_FLOW_CYCLES;
import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.SAVINGS_MEDIAN_CYCLES;
import static com.mikeshaggy.backend.insight.detector.InsightFormat.pln;

/**
 * Savings are paying for everyday spending and are nearly gone:
 * <pre>Σ net(savings → salary wallet) over the last 2 closed cycles &gt; 0
 *   and savings balance &lt; median whole-cycle variable spend of the last 6 closed cycles</pre>
 * Net flows are the Stage 6.2 transfer summary from the salary wallet's side ({@code in − out}: positive = money came
 * in from savings; fund movements and self-transfers are not transfers there). A cycle without transfers to or from
 * savings is a real zero. Missing savings wallet, fewer than two closed cycles or no closed-cycle history → no insight.
 */
@Component
public class SavingsFundingDailyDetector implements InsightDetector {

    @Override
    public InsightType type() {
        return InsightType.SAVINGS_FUNDING_DAILY;
    }

    @Override
    public Optional<InsightCandidate> detect(InsightContext ctx) {
        Optional<WalletSnapshot> savingsWallet = ctx.savingsWallet();
        if (savingsWallet.isEmpty() || savingsWallet.get().balance() == null) {
            return Optional.empty();
        }
        WalletSnapshot savings = savingsWallet.get();
        TransferSummaryResponse summary = ctx.transferSummary();
        if (summary == null || summary.cycles().size() < SAVINGS_FLOW_CYCLES) {
            return Optional.empty();
        }
        List<CycleSummary> recent = summary.cycles().subList(0, SAVINGS_FLOW_CYCLES);
        if (recent.stream().anyMatch(cycle -> cycle.state() != CycleState.CLOSED)) {
            return Optional.empty();
        }

        BigDecimal netInflow = zero();
        List<Map<String, Object>> perCycle = new ArrayList<>();
        for (CycleSummary cycle : recent) {
            BigDecimal net = netWith(cycle, savings.id());
            netInflow = netInflow.add(net);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("start", cycle.start().toString());
            row.put("end", cycle.end().toString());
            row.put("net", net);
            perCycle.add(row);
        }
        if (netInflow.signum() <= 0) {
            return Optional.empty();
        }

        List<BigDecimal> totals = ctx.cycleHistory().stream()
                .limit(SAVINGS_MEDIAN_CYCLES)
                .map(CycleVariableTotal::variableTotal)
                .toList();
        if (totals.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal typicalCycleVariable = Quantiles.median(totals);
        BigDecimal balance = money(savings.balance());
        if (balance.compareTo(typicalCycleVariable) >= 0) {
            return Optional.empty();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("savingsWalletId", savings.id());
        payload.put("savingsWalletName", savings.name());
        payload.put("netInflow", netInflow);
        payload.put("cycles", perCycle);
        payload.put("savingsBalance", balance);
        payload.put("typicalCycleVariable", typicalCycleVariable);
        payload.put("typicalCycles", totals.size());
        String name = InsightFormat.clip(savings.name(), 60);
        return Optional.of(new InsightCandidate(
                type(),
                InsightSeverity.WARN,
                "savings:" + savings.id(),
                "Savings are funding daily spending",
                "%s net came from %s over the last %d cycles; %s now holds %s, less than one typical cycle of variable spending (%s)."
                        .formatted(pln(netInflow), name, SAVINGS_FLOW_CYCLES, name, pln(balance),
                                pln(typicalCycleVariable)),
                payload,
                InsightActionType.LINK,
                "/wallets/transfers"));
    }

    private static BigDecimal netWith(CycleSummary cycle, Integer savingsWalletId) {
        return cycle.counterWallets().stream()
                .filter(flow -> savingsWalletId.equals(flow.walletId()))
                .map(CounterWalletFlow::net)
                .map(net -> money(net))
                .findFirst()
                .orElse(zero());
    }
}
