package com.mikeshaggy.backend.dashboard.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.dashboard.dto.DashboardData;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.service.DashboardOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(DashboardController.BASE_URL)
@RequiredArgsConstructor
public class DashboardController {

    public static final String BASE_URL = "/api/dashboard";

    private final DashboardOrchestrator dashboardOrchestrator;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<DashboardData> getDashboardData(
            @RequestParam Integer walletId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType
    ) {
        UUID userId = currentUserProvider.getCurrentUserId();
        DashboardData data = dashboardOrchestrator.getDashboardData(
                userId, walletId, startDate, endDate, periodType);
        return ResponseEntity.ok(data);
    }
}
