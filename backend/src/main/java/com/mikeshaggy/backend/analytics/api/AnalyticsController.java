package com.mikeshaggy.backend.analytics.api;

import com.mikeshaggy.backend.analytics.comparison.BaselineComparisonDto;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.breakdown.CategoryBreakdownDto;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonResponseDto;
import com.mikeshaggy.backend.analytics.cyclecomparison.CycleComparisonService;
import com.mikeshaggy.backend.analytics.daily.HeatmapResponseDto;
import com.mikeshaggy.backend.analytics.breakdown.ImportanceBreakdownDto;
import com.mikeshaggy.backend.analytics.insight.InsightResponseDto;
import com.mikeshaggy.backend.analytics.overview.AnalyticsSummaryDto;
import com.mikeshaggy.backend.analytics.overview.AnalyticsSummaryService;
import com.mikeshaggy.backend.analytics.overview.PeriodOverviewDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.comparison.BaselineService;
import com.mikeshaggy.backend.analytics.breakdown.CategoryBreakdownService;
import com.mikeshaggy.backend.analytics.daily.HeatmapService;
import com.mikeshaggy.backend.analytics.breakdown.ImportanceBreakdownService;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.analytics.overview.PeriodOverviewService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(AnalyticsController.BASE_URL)
@RequiredArgsConstructor
public class AnalyticsController {

    public static final String BASE_URL = "/api/wallets/{walletId}/analytics";

    private final PeriodService periodService;
    private final AnalyticsSummaryService analyticsSummaryService;
    private final PeriodOverviewService periodOverviewService;
    private final SpendingProjectionService spendingProjectionService;
    private final CategoryBreakdownService categoryBreakdownService;
    private final ImportanceBreakdownService importanceBreakdownService;
    private final HeatmapService heatmapService;
    private final BaselineService baselineService;
    private final CycleComparisonService cycleComparisonService;
    private final InsightEngine insightEngine;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/period")
    public ResponseEntity<PeriodDto> resolvePeriod(
            @PathVariable Integer walletId,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = currentUserProvider.getCurrentUserId();
        PeriodDto period = periodService.resolve(periodType, walletId, userId, startDate, endDate);
        return ResponseEntity.ok(period);
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDto> getAnalyticsSummary(
            @PathVariable Integer walletId,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = currentUserProvider.getCurrentUserId();
        AnalyticsSummaryDto response = analyticsSummaryService.getSummary(
                walletId, userId, periodType, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/overview")
    public ResponseEntity<PeriodOverviewDto> getPeriodOverview(
            @PathVariable Integer walletId,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = currentUserProvider.getCurrentUserId();
        PeriodOverviewDto response = periodOverviewService.getPeriodOverview(
                walletId, userId, periodType, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/projections")
    public ResponseEntity<SpendingProjectionDto> getSpendingProjection(
            @PathVariable Integer walletId,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = currentUserProvider.getCurrentUserId();
        SpendingProjectionDto response = spendingProjectionService.getSpendingProjection(
                walletId, userId, periodType, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/category-breakdown")
    public ResponseEntity<CategoryBreakdownDto> getCategoryBreakdown(
            @PathVariable Integer walletId,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = currentUserProvider.getCurrentUserId();
        CategoryBreakdownDto response = categoryBreakdownService.getCategoryBreakdown(
                walletId, userId, periodType, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/importance-breakdown")
    public ResponseEntity<ImportanceBreakdownDto> getImportanceBreakdown(
            @PathVariable Integer walletId,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = currentUserProvider.getCurrentUserId();
        ImportanceBreakdownDto response = importanceBreakdownService.getImportanceBreakdown(
                walletId, userId, periodType, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/heatmap")
    public ResponseEntity<HeatmapResponseDto> getHeatmap(
            @PathVariable Integer walletId,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = currentUserProvider.getCurrentUserId();
        HeatmapResponseDto response = heatmapService.getHeatmap(walletId, userId, periodType, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/baseline")
    public ResponseEntity<BaselineComparisonDto> getBaseline(
            @PathVariable Integer walletId,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = currentUserProvider.getCurrentUserId();
        BaselineComparisonDto response = baselineService.getBaseline(walletId, userId, periodType, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cycle-comparison")
    public ResponseEntity<CycleComparisonResponseDto> getCycleComparison(
            @PathVariable Integer walletId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) Integer baselineCycles,
            @RequestParam(required = false) List<Integer> categoryIds,
            @RequestParam(defaultValue = "ALL") CategoryAggregationMode categoryMode,
            @RequestParam(defaultValue = "false") boolean includeIncome,
            @RequestParam(required = false) List<Importance> importance) {
        UUID userId = currentUserProvider.getCurrentUserId();
        CycleComparisonResponseDto response = cycleComparisonService.getCycleComparison(
                walletId, userId, asOfDate, baselineCycles, categoryIds, categoryMode, includeIncome, importance);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/insights")
    public ResponseEntity<InsightResponseDto> getInsights(
            @PathVariable Integer walletId,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = currentUserProvider.getCurrentUserId();
        InsightResponseDto response = insightEngine.getInsights(walletId, userId, periodType, startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
