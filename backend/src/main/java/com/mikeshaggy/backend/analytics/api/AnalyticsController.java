package com.mikeshaggy.backend.analytics.api;

import com.mikeshaggy.backend.analytics.dto.MonthlyOverviewDto;
import com.mikeshaggy.backend.analytics.service.MonthlyOverviewService;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping(AnalyticsController.BASE_URL)
@RequiredArgsConstructor
public class AnalyticsController {

    public static final String BASE_URL = "/api/wallets/{walletId}/analytics";

    private final MonthlyOverviewService monthlyOverviewService;
    private final CurrentUserProvider currentUserProvider;

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
