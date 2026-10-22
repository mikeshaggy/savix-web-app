package com.mikeshaggy.backend.insight.detector;

import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;

import java.util.Map;

/**
 * What a detector reports for the current cycle. {@code dedupeKey} identifies the event within
 * {@code (wallet, type, cycle start)} — e.g. a category id, a transaction id, or a constant for once-per-cycle
 * events; the same key is never stored twice for a cycle. {@code payload} carries the figures behind the
 * insight (stored as {@code jsonb}; {@code BigDecimal} values survive exactly) and may be {@code null}.
 * <p>
 * Validated by {@code InsightService} before anything is stored: type equal to the detector's, non-null
 * severity, non-blank key and title within the column limits, and an {@code actionRef} only with an action.
 */
public record InsightCandidate(
        InsightType type,
        InsightSeverity severity,
        String dedupeKey,
        String title,
        String description,
        Map<String, Object> payload,
        InsightActionType actionType,
        String actionRef
) {
    public static final int MAX_DEDUPE_KEY_LENGTH = 120;
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_ACTION_REF_LENGTH = 120;
}
