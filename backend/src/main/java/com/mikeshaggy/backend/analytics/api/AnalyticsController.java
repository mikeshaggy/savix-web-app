package com.mikeshaggy.backend.analytics.api;

import com.mikeshaggy.backend.analytics.dto.PeriodOverviewDto;
import com.mikeshaggy.backend.analytics.service.PeriodOverviewService;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
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
@RequestMapping(AnalyticsController.BASE_URL)
@RequiredArgsConstructor
public class AnalyticsController {

    public static final String BASE_URL = "/api/wallets/{walletId}/analytics";

    private final PeriodOverviewService periodOverviewService;
    private final CurrentUserProvider currentUserProvider;

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
}