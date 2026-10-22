package com.mikeshaggy.backend.insight.service;

import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.repository.InsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only insert path for insights. Each insert runs in its <em>own</em> read-write transaction
 * ({@code REQUIRES_NEW}), so it never writes through a caller's read-only transaction, and a unique-key
 * violation rolls back this insert alone — the caller's transaction (if any) is suspended, not marked
 * rollback-only. The violation itself propagates; {@link InsightService} decides whether it was the expected
 * duplicate.
 */
@Component
@RequiredArgsConstructor
public class InsightWriter {

    private final InsightRepository insightRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Insight insert(Insight insight) {
        // IDENTITY ids: the INSERT is issued here, inside this transaction, not at a later flush
        return insightRepository.saveAndFlush(insight);
    }
}
