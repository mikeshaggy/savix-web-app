package com.mikeshaggy.backend.insight.detector;

import java.util.List;
import java.util.Optional;

/**
 * A rule that can report several distinct events in one evaluation (Stage 7.3) — one candidate per event, each with
 * its own dedupe key, in a deterministic order. {@link #detect} is the first of them.
 */
public abstract class MultiInsightDetector implements InsightDetector {

    @Override
    public abstract List<InsightCandidate> detectAll(InsightContext ctx);

    @Override
    public final Optional<InsightCandidate> detect(InsightContext ctx) {
        return detectAll(ctx).stream().findFirst();
    }
}
