package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.insight.domain.InsightType;

import java.util.Optional;

/**
 * One Insights v2 event rule (decision I1): it either fires for the current cycle or it does not — "nothing to
 * report" is {@link Optional#empty()}, never a filler insight. Implementations are Spring beans; every bean of
 * this type is registered with {@code InsightService} (no registration list to maintain, and an empty registry
 * is valid).
 * <p>
 * Contract:
 * <ul>
 *   <li>Pure with respect to persistence — read inputs from the {@link InsightContext} only; never write.</li>
 *   <li>Load only the context inputs the rule needs; each is fetched lazily, once per evaluation.</li>
 *   <li>An unavailable input ({@code forecast()} empty, an exception from an accessor) means "cannot decide":
 *       return empty or let the exception propagate — never substitute zero. A thrown exception skips this
 *       detector for the evaluation and does not affect the others.</li>
 *   <li>Every candidate's {@link InsightCandidate#type()} equals {@link #type()}.</li>
 * </ul>
 */
public interface InsightDetector {

    InsightType type();

    Optional<InsightCandidate> detect(InsightContext ctx);
}
