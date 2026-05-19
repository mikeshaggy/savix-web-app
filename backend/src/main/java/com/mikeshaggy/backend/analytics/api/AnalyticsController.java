package com.mikeshaggy.backend.analytics.api;

import com.mikeshaggy.backend.analytics.dto.MonthlyOverviewDto;
import com.mikeshaggy.backend.analytics.dto.PeriodOverviewDto;
import com.mikeshaggy.backend.analytics.service.MonthlyOverviewService;
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
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@RestController
@RequestMapping(AnalyticsController.BASE_URL)
@RequiredArgsConstructor
public class AnalyticsController {

    public static final String BASE_URL = "/api/wallets/{walletId}/analytics";

    private final MonthlyOverviewService monthlyOverviewService;
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

    @GetMapping("/monthly-overview")
    public ResponseEntity<MonthlyOverviewDto> getMonthlyOverview(
            @PathVariable Integer walletId,
            @RequestParam(required = false) String month) {
        YearMonth requestedMonth = parseMonth(month);
        MonthlyOverviewDto response = monthlyOverviewService.getMonthlyOverview(
                walletId,
                currentUserProvider.getCurrentUserId(),
                requestedMonth);
        return ResponseEntity.ok(response);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new IllegalArgumentException("month query parameter is required");
        }

        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("month must use YYYY-MM format", ex);
        }
    }
}
