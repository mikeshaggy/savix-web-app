package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse.CycleMinimum;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.mikeshaggy.backend.insight.detector.InsightFormat.pln;

/**
 * The previous pay cycle — the closed cycle that ended the day before the current one started — had a daily closing
 * ledger balance below zero ({@code min balance_after < 0}, Stage 6.1 per-cycle minimum). Historical ledger balances
 * only: never the current wallet balance and never a projection; the open cycle is never looked at. Keyed by the
 * closed cycle's start (once per closed cycle, whatever cycle is current later).
 */
@Component
public class PrePaydayNegativeDetector implements InsightDetector {

    @Override
    public InsightType type() {
        return InsightType.PRE_PAYDAY_NEGATIVE;
    }

    @Override
    public Optional<InsightCandidate> detect(InsightContext ctx) {
        Optional<CycleMinimum> previous = ctx.sparklineMinima().stream()
                .filter(minimum -> minimum.cycleEnd().plusDays(1).equals(ctx.cycle().start()))
                .findFirst();
        if (previous.isEmpty() || previous.get().minBalance() == null
                || previous.get().minBalance().signum() >= 0) {
            return Optional.empty();
        }
        CycleMinimum minimum = previous.get();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cycleStart", minimum.cycleStart().toString());
        payload.put("cycleEnd", minimum.cycleEnd().toString());
        payload.put("minBalance", minimum.minBalance());
        payload.put("date", minimum.date() == null ? null : minimum.date().toString());
        return Optional.of(new InsightCandidate(
                type(),
                InsightSeverity.WARN,
                "cycle:" + minimum.cycleStart(),
                "Balance went below zero before payday",
                "Lowest balance %s on %s, in the cycle %s – %s.".formatted(pln(minimum.minBalance()), minimum.date(),
                        minimum.cycleStart(), minimum.cycleEnd()),
                payload,
                InsightActionType.LINK,
                "/wallets/balance-history"));
    }
}
