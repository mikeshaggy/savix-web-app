package com.mikeshaggy.backend.fund.domain;

public enum FundStatus {
    /** User is still saving toward the goal. */
    ACTIVE,
    /** Goal was successfully reached and explicitly finished. Distinct from ARCHIVED. */
    COMPLETED,
    /** Hidden/removed from the normal flow. Not necessarily completed. */
    ARCHIVED
}
