package com.mikeshaggy.backend.insight.service;

import com.mikeshaggy.backend.insight.detector.InsightCandidate;
import com.mikeshaggy.backend.insight.detector.InsightContext;
import com.mikeshaggy.backend.insight.detector.InsightDetector;
import com.mikeshaggy.backend.insight.domain.InsightType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs every registered {@link InsightDetector} against one context and returns the valid candidates, at most one
 * per {@code (type, dedupeKey)}, in run order (by type, then implementation name — independent of bean order).
 * <p>
 * The detection phase is read-only and runs with {@link Propagation#NOT_SUPPORTED}: a caller's transaction is
 * suspended, so the context's reads run in their own read-only transactions and a failing read cannot mark the
 * caller rollback-only. Whatever the scope binds (an EntityManager and its connection) is released when this
 * method returns — before {@link InsightService} stores anything, so an evaluation never holds one connection
 * while waiting for another.
 * <p>
 * A detector that throws (including a failing context input) or returns an invalid candidate is logged and
 * skipped; the other detectors still run.
 */
@Slf4j
@Component
public class InsightDetectorRunner {

    private final List<InsightDetector> detectors;

    public InsightDetectorRunner(ObjectProvider<InsightDetector> detectors) {
        this.detectors = detectors.orderedStream()
                .sorted(Comparator.comparing(InsightDetector::type)
                        .thenComparing(detector -> detector.getClass().getName()))
                .toList();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<InsightCandidate> detect(InsightContext context) {
        List<InsightCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (InsightDetector detector : detectors) {
            InsightType type = detector.type();
            List<InsightCandidate> results;
            try {
                results = detector.detectAll(context);
            } catch (RuntimeException e) {
                log.warn("insight.detector.failed type={} walletId={} cycleStart={} reason={}",
                        type, context.wallet().id(), context.cycle().start(), e.toString(), e);
                continue;
            }
            if (results == null) {
                continue;
            }
            for (InsightCandidate candidate : results) {
                try {
                    validate(type, candidate);
                } catch (IllegalArgumentException e) {
                    invalid(type, context, e.getMessage());
                    continue;
                }
                if (seen.add(candidate.type() + "\u0000" + candidate.dedupeKey())) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    static void invalid(InsightType type, InsightContext context, String reason) {
        log.warn("insight.detector.invalid type={} walletId={} cycleStart={} reason={}",
                type, context.wallet().id(), context.cycle().start(), reason);
    }

    private static void validate(InsightType detectorType, InsightCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate is null");
        }
        if (candidate.type() != detectorType) {
            throw new IllegalArgumentException("candidate type " + candidate.type() + " != detector type");
        }
        if (candidate.severity() == null) {
            throw new IllegalArgumentException("severity is required");
        }
        requireText(candidate.dedupeKey(), "dedupeKey", InsightCandidate.MAX_DEDUPE_KEY_LENGTH);
        requireText(candidate.title(), "title", InsightCandidate.MAX_TITLE_LENGTH);
        if (candidate.actionRef() != null) {
            if (candidate.actionType() == null) {
                throw new IllegalArgumentException("actionRef without actionType");
            }
            if (candidate.actionRef().length() > InsightCandidate.MAX_ACTION_REF_LENGTH) {
                throw new IllegalArgumentException("actionRef longer than " + InsightCandidate.MAX_ACTION_REF_LENGTH);
            }
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " longer than " + maxLength);
        }
    }
}
