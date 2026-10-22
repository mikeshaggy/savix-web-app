package com.mikeshaggy.backend.insight.domain;

/**
 * How an insight is presented (Stage 7.3), decided by its {@link InsightType} — never by title or wording:
 * <ul>
 *   <li>{@code NOTABLE} — something about the money now that deserves attention: a current condition or a recent
 *       event the user may act on. These are the insights the dashboard's "Notable" list ranks and caps (at most four
 *       visible).</li>
 *   <li>{@code LIFECYCLE} — an informational summary of the pay-cycle lifecycle (a cycle closed). Persisted and
 *       deduplicated like any other insight, but shown apart from the notable list so it never takes one of its
 *       slots.</li>
 * </ul>
 */
public enum InsightKind {
    NOTABLE,
    LIFECYCLE
}
