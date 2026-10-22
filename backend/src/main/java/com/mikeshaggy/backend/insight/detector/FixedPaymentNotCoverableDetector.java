package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.analytics.forecast.CommittedOccurrenceDto;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CommittedOccurrence;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.OccurrenceCoverage;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mikeshaggy.backend.insight.detector.InsightFormat.pln;

/**
 * A pending fixed payment due before the next payday that the low (pessimistic) path cannot cover on its due date —
 * the Stage 4 {@code SHORT} sub-rule, read through {@link ForecastV2Calculator#coverageWalk} so the forecast status and
 * this insight can never disagree:
 * <pre>amount_o &gt; walletBalance − Σ committed due before o − pessimisticVariable × clamp(daysUntil(o), 0, R) / R</pre>
 * with the forecast's own committed occurrences (Stage 3 OVERDUE / DUE_SOON / LATER_THIS_CYCLE — all due before the
 * expected payday), {@code walletBalance = discretionaryNow + committed}, {@code pessimisticVariable =
 * expectedVariableRemainingLow} and {@code R} = days left after today. Never an end-of-cycle balance. OPEN cycles with a
 * Forecast v2 only. Reports the first uncoverable occurrence (later ones follow from it), keyed by the occurrence.
 */
@Component
public class FixedPaymentNotCoverableDetector implements InsightDetector {

    @Override
    public InsightType type() {
        return InsightType.FIXED_PAYMENT_NOT_COVERABLE;
    }

    @Override
    public Optional<InsightCandidate> detect(InsightContext ctx) {
        PayCycle cycle = ctx.cycle();
        Optional<ForecastV2Dto> maybeForecast = ctx.forecast();
        if (cycle.state() != CycleState.OPEN || maybeForecast.isEmpty()) {
            return Optional.empty();
        }
        ForecastV2Dto forecast = maybeForecast.get();
        if (forecast.status() == null || forecast.expectedVariableRemainingLow() == null
                || forecast.discretionaryNow() == null || forecast.committed() == null) {
            return Optional.empty();
        }
        List<CommittedOccurrenceDto> committed = forecast.committedOccurrences().stream()
                .filter(row -> row.dueDate() != null && row.expectedAmount() != null && row.bucket() != null)
                .filter(row -> row.dueDate().isBefore(cycle.expectedNextAnchor()))
                .toList();
        if (committed.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal walletBalance = forecast.discretionaryNow().add(forecast.committed());
        int daysRemaining = (int) Math.max(0, ChronoUnit.DAYS.between(ctx.today(), cycle.end()));
        List<OccurrenceCoverage> walk = ForecastV2Calculator.coverageWalk(
                committed.stream()
                        .map(row -> new CommittedOccurrence(row.dueDate(), row.expectedAmount(), row.bucket()))
                        .toList(),
                walletBalance, forecast.expectedVariableRemainingLow(), ctx.today(), daysRemaining);

        int firstUncoverable = -1;
        int uncoverableCount = 0;
        for (int i = 0; i < walk.size(); i++) {
            if (!walk.get(i).coverable()) {
                uncoverableCount++;
                if (firstUncoverable < 0) {
                    firstUncoverable = i;
                }
            }
        }
        if (firstUncoverable < 0) {
            return Optional.empty();
        }
        CommittedOccurrenceDto row = committed.get(firstUncoverable);
        if (row.occurrenceId() == null) {
            return Optional.empty();
        }
        BigDecimal available = walk.get(firstUncoverable).availableBefore();
        BigDecimal shortfall = row.expectedAmount().subtract(available);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("occurrenceId", row.occurrenceId());
        payload.put("title", row.title());
        payload.put("dueDate", row.dueDate().toString());
        payload.put("amount", row.expectedAmount());
        payload.put("availableOnDueDate", available);
        payload.put("shortfall", shortfall);
        payload.put("uncoverableCount", uncoverableCount);
        return Optional.of(new InsightCandidate(
                type(),
                InsightSeverity.ALERT,
                "occurrence:" + row.occurrenceId(),
                InsightFormat.clip(row.title(), 120) + " may not be covered",
                "%s due %s; on the low path about %s is left by then (short %s)."
                        .formatted(pln(row.expectedAmount()), row.dueDate(), pln(available), pln(shortfall)),
                payload,
                InsightActionType.LINK,
                "/transactions/fixed-payments"));
    }
}
