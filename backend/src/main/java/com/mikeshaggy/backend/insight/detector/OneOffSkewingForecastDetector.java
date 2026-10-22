package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.analytics.forecast.OneOffDto;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.ONE_OFF_MIN_IMPACT;
import static com.mikeshaggy.backend.insight.detector.InsightFormat.pln;

/**
 * A Forecast v2 one-off (Stage 4.6) that is still in the spending pace and moves the typical end balance by at least
 * 200 PLN ({@code excluded = false} and {@code impactOnExpectedEndBalance ≥ 200}). One insight per transaction, keyed
 * by its id, with the {@code EXCLUDE_TRANSACTION} action. Once excluded the forecast reports it with
 * {@code excluded = true} and zero impact, so it is no longer detected and — a conditional type — no longer listed.
 * No forecast (flag off, awaiting salary, not applicable) → nothing.
 */
@Component
public class OneOffSkewingForecastDetector extends MultiInsightDetector {

    @Override
    public InsightType type() {
        return InsightType.ONE_OFF_SKEWING_FORECAST;
    }

    @Override
    public List<InsightCandidate> detectAll(InsightContext ctx) {
        Optional<ForecastV2Dto> forecast = ctx.forecast();
        if (forecast.isEmpty()) {
            return List.of();
        }
        return forecast.get().oneOffs().stream()
                .filter(oneOff -> !oneOff.excluded())
                .filter(oneOff -> oneOff.transactionId() != null)
                .filter(oneOff -> oneOff.impactOnExpectedEndBalance() != null
                        && oneOff.impactOnExpectedEndBalance().compareTo(ONE_OFF_MIN_IMPACT) >= 0)
                .map(this::candidate)
                .toList();
    }

    private InsightCandidate candidate(OneOffDto oneOff) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", oneOff.transactionId());
        payload.put("title", oneOff.title());
        payload.put("date", oneOff.date() == null ? null : oneOff.date().toString());
        payload.put("categoryName", oneOff.categoryName());
        payload.put("amount", oneOff.amount());
        payload.put("impactOnExpectedEndBalance", oneOff.impactOnExpectedEndBalance());
        payload.put("impactOnExpectedVariableRemaining", oneOff.impactOnExpectedVariableRemaining());
        return new InsightCandidate(
                type(),
                InsightSeverity.INFO,
                "transaction:" + oneOff.transactionId(),
                "One-off is skewing your forecast",
                "%s (%s on %s) lowers the expected end balance by %s. Exclude it if it will not repeat."
                        .formatted(InsightFormat.clip(oneOff.title(), 80), pln(oneOff.amount()), oneOff.date(),
                                pln(oneOff.impactOnExpectedEndBalance())),
                payload,
                InsightActionType.EXCLUDE_TRANSACTION,
                oneOff.transactionId().toString());
    }
}
