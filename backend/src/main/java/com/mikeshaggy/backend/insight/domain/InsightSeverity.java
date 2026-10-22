package com.mikeshaggy.backend.insight.domain;

/** Insight severity, least severe first; lists are ordered most severe first (see {@code InsightService}). */
public enum InsightSeverity {
    INFO,
    WARN,
    ALERT
}
