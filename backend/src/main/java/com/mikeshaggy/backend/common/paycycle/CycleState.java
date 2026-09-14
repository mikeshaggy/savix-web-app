package com.mikeshaggy.backend.common.paycycle;

/**
 * State of a pay cycle, derived on read (never persisted).
 * <ul>
 *   <li>{@code OPEN} — today is before the expected next salary anchor.</li>
 *   <li>{@code AWAITING_SALARY} — today is on/after the expected anchor and no newer anchor exists.</li>
 *   <li>{@code CLOSED} — a newer salary anchor exists; both boundaries are facts.</li>
 * </ul>
 */
public enum CycleState {
    OPEN,
    AWAITING_SALARY,
    CLOSED
}
