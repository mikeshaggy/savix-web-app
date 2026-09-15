package com.mikeshaggy.backend.analytics.cyclecomparison;

/** How the comparison windows were resolved — so a calendar month is never mistaken for a pay cycle. */
public enum CycleComparisonBaselineKind {
    /** Actual salary-to-salary cycles of the user's salary wallet. */
    PAY_CYCLE,
    /** Calendar months: the user has no pay cycle at all (no anchor category / no salary anchor). */
    MONTHLY_FALLBACK,
    /** Calendar months: the user has pay cycles, but the requested wallet is not the salary wallet. */
    NOT_SALARY_WALLET
}
