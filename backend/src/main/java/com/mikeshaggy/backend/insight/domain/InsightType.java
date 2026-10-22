package com.mikeshaggy.backend.insight.domain;

/**
 * Insights v2 event types (decision I1): one detector per type. Persisted by name in {@code insights.type}
 * (varchar 40), so a constant is never renamed once rows exist. The legacy {@code analytics.insight.InsightType}
 * rules are unrelated and removed in Stage 7.4.
 * <p>
 * Each type carries its lifecycle (Stage 7.3), applied by {@code InsightService}:
 * <ul>
 *   <li>{@link #recurrence()} — when an already stored key may be stored again (see {@link Recurrence}).</li>
 *   <li>{@link #conditional()} — a <em>condition</em> (true while the data says so: an un-excluded one-off, a payment
 *       that cannot be covered) rather than an <em>event</em> (a cycle closed). A stored condition is listed only
 *       while this evaluation's detector still reports the same key; otherwise it is hidden — never deleted and
 *       never dismissed — so a stale action (e.g. "exclude" on an already excluded transaction) is not offered. While
 *       it is still detected its content is refreshed in place; an event keeps the snapshot of its first detection.</li>
 *   <li>{@link #kind()} — how it is presented: notable or a lifecycle summary (see {@link InsightKind}).</li>
 * </ul>
 */
public enum InsightType {
    CATEGORY_ABOVE_TYPICAL(Recurrence.ONCE_PER_CYCLE, true),
    SAVINGS_FUNDING_DAILY(Recurrence.ONCE_PER_CYCLE, true),
    /** Keyed by the closed cycle it describes, so it is never re-emitted for that cycle. */
    PRE_PAYDAY_NEGATIVE(Recurrence.ONCE_PER_WALLET, false),
    ONE_OFF_SKEWING_FORECAST(Recurrence.ONCE_PER_CYCLE, true),
    FIXED_PAYMENT_NOT_COVERABLE(Recurrence.ONCE_PER_CYCLE, true),
    FIXED_PAYMENT_AMOUNT_DRIFT(Recurrence.ONCE_PER_CYCLE, true),
    /** A dismissed merchant suggestion stays dismissed in later cycles. */
    RECURRING_MERCHANT_DETECTED(Recurrence.UNTIL_DISMISSED, true),
    /** Keyed by the closed cycle it describes, so it is never re-emitted for that cycle; a lifecycle summary. */
    CYCLE_CLOSED(Recurrence.ONCE_PER_WALLET, false, InsightKind.LIFECYCLE),
    SALARY_NOT_RECEIVED(Recurrence.ONCE_PER_CYCLE, false),
    HIGH_IMPULSE_SPENDING(Recurrence.ONCE_PER_CYCLE, true);

    /** When a candidate whose key was already stored for the wallet may be stored again. */
    public enum Recurrence {
        /** Once per {@code (cycle start, key)}; the same key may recur in a later cycle (the Stage 7.2 rule). */
        ONCE_PER_CYCLE,
        /** Once per key for the wallet, whatever the cycle it was stored under. */
        ONCE_PER_WALLET,
        /** Once per cycle, and never again after the key was dismissed in any cycle. */
        UNTIL_DISMISSED
    }

    private final Recurrence recurrence;
    private final boolean conditional;
    private final InsightKind kind;

    InsightType(Recurrence recurrence, boolean conditional) {
        this(recurrence, conditional, InsightKind.NOTABLE);
    }

    InsightType(Recurrence recurrence, boolean conditional, InsightKind kind) {
        this.recurrence = recurrence;
        this.conditional = conditional;
        this.kind = kind;
    }

    /** Notable (ranked, capped on the dashboard) or lifecycle (shown apart) — see {@link InsightKind}. */
    public InsightKind kind() {
        return kind;
    }

    public Recurrence recurrence() {
        return recurrence;
    }

    public boolean conditional() {
        return conditional;
    }
}
