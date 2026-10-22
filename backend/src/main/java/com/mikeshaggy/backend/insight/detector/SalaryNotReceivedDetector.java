package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.mikeshaggy.backend.insight.detector.DetectorThresholds.SALARY_GRACE_DAYS;

/**
 * The salary is clearly late: the cycle is {@code AWAITING_SALARY} and today ≥ expected next anchor + 5 days. The state
 * comes from {@code PayCycleService} (only a salary anchor transaction starts a cycle — other income never does).
 * Informational, no action; keyed by the expected anchor, once per cycle.
 */
@Component
public class SalaryNotReceivedDetector implements InsightDetector {

    @Override
    public InsightType type() {
        return InsightType.SALARY_NOT_RECEIVED;
    }

    @Override
    public Optional<InsightCandidate> detect(InsightContext ctx) {
        PayCycle cycle = ctx.cycle();
        LocalDate expected = cycle.expectedNextAnchor();
        if (cycle.state() != CycleState.AWAITING_SALARY || expected == null
                || ctx.today().isBefore(expected.plusDays(SALARY_GRACE_DAYS))) {
            return Optional.empty();
        }
        long daysLate = ChronoUnit.DAYS.between(expected, ctx.today());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cycleStart", cycle.start().toString());
        payload.put("expectedPayday", expected.toString());
        payload.put("daysLate", daysLate);
        return Optional.of(new InsightCandidate(
                type(),
                InsightSeverity.INFO,
                "expected:" + expected,
                "Salary not received yet",
                "Salary was expected on %s (%d days ago) and has not arrived.".formatted(expected, daysLate),
                payload,
                null,
                null));
    }
}
