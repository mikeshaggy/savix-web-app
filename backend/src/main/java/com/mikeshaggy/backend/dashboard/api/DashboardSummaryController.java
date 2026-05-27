package com.mikeshaggy.backend.dashboard.api;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.dashboard.dto.DashboardSummaryDto;
import com.mikeshaggy.backend.dashboard.service.DashboardSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(DashboardSummaryController.BASE_URL)
@RequiredArgsConstructor
public class DashboardSummaryController {

    public static final String BASE_URL = "/api/wallets/{walletId}/dashboard";

    private final DashboardSummaryService dashboardSummaryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDto> getDashboardSummary(
            @PathVariable Integer walletId,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "INCLUDED_IN_TOP_CATEGORIES") CategoryAggregationMode categoryMode) {
        UUID userId = currentUserProvider.getCurrentUserId();
        DashboardSummaryDto summary = dashboardSummaryService.getSummary(
                walletId, userId, periodType, startDate, endDate, asOfDate, categoryMode);
        return ResponseEntity.ok(summary);
    }
}
