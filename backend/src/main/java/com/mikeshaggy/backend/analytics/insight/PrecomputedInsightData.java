package com.mikeshaggy.backend.analytics.insight;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.PeriodTotals;

import java.math.BigDecimal;

/**
 * Precomputed values supplied to InsightEngine.getInsightsForWindow by an orchestrating
 * service that has already fetched these figures. Avoids redundant DB queries.
 *
 * <p>{@code projection} may be null when the period is historical and no projection is available.
 * <p>{@code currentCategories} and {@code compareCategories} may be null when not precomputed
 * (e.g. standalone calls or when a different aggregation mode was used); InsightEngine will query
 * {@code CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES} on its own in that case.
 */
public record PrecomputedInsightData(
        SpendingProjectionDto projection,
        PeriodTotals currentTotals,
        BigDecimal compareExpenses,
        CategoryAggregationResult currentCategories,
        CategoryAggregationResult compareCategories
) {}
