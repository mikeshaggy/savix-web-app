package com.mikeshaggy.backend.insight.dto;

/**
 * Whether insights apply to the requested wallet. Only {@code AVAILABLE} evaluates detectors; every other value
 * comes with an empty list and nothing read from or written to the insights table.
 * <ul>
 *   <li>{@code AVAILABLE} — the salary wallet's current pay cycle; an empty list means "nothing unusual".</li>
 *   <li>{@code FEATURE_DISABLED} — {@code insights-v2} is off.</li>
 *   <li>{@code NO_PAY_CYCLE} — no salary wallet, anchor category or salary anchor yet.</li>
 *   <li>{@code NOT_SALARY_WALLET} — the wallet is not the user's salary wallet (no insights section).</li>
 *   <li>{@code PAY_CYCLE_V2_INACTIVE} — the resolved pay-cycle period is not the pay-cycle-v2 cycle.</li>
 * </ul>
 */
public enum InsightAvailability {
    AVAILABLE,
    FEATURE_DISABLED,
    NO_PAY_CYCLE,
    NOT_SALARY_WALLET,
    PAY_CYCLE_V2_INACTIVE
}
