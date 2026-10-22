package com.mikeshaggy.backend.insight.service;

import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.repository.InsightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only write path for insight content (inserts and condition refreshes). Each write runs in its <em>own</em>
 * read-write transaction ({@code REQUIRES_NEW}), so it never writes through a caller's read-only transaction, and a
 * unique-key violation rolls back this insert alone — the caller's transaction (if any) is suspended, not marked
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

    /**
     * Rewrites a condition insight's content with the current candidate (Stage 7.3), in its own read-write transaction
     * like {@link #insert}. Returns the number of rows changed: {@code 0} when the row was dismissed (or removed) since it
     * was read — a dismissed insight is never revived or rewritten.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int refresh(Insight current, InsightContent content) {
        return insightRepository.refreshContent(current.getId(), current.getUserId(), content.severity(),
                content.title(), content.description(), content.payload(), content.actionType(), content.actionRef());
    }

    /** The mutable part of an insight — everything except its identity, timestamps and dismissal state. */
    public record InsightContent(InsightSeverity severity, String title, String description, String payload,
                                 InsightActionType actionType, String actionRef) {
    }
}
