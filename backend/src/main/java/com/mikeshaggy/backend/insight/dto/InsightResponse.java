package com.mikeshaggy.backend.insight.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One active insight. {@code id} is the dismissal handle ({@code DELETE /api/insights/{id}}); {@code payload} is
 * the detector's figures as stored (decimals exact), {@code null} when the detector stored none;
 * {@code actionType}/{@code actionRef} are both {@code null} for an informational insight.
 */
public record InsightResponse(
        Long id,
        InsightType type,
        InsightSeverity severity,
        LocalDate cycleStart,
        String title,
        String description,
        JsonNode payload,
        InsightActionType actionType,
        String actionRef,
        Instant createdAt
) {
}
