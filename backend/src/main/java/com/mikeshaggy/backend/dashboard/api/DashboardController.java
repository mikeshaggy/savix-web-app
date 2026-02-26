package com.mikeshaggy.backend.dashboard.api;

import com.mikeshaggy.backend.dashboard.dto.DashboardData;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(DashboardController.BASE_URL)
@RequiredArgsConstructor
public class DashboardController {

    public static final String BASE_URL = "/api/dashboard";

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardData> getDashboardData(
            @RequestParam Integer walletId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "PAY_CYCLE") PeriodType periodType
    ) {
        DashboardData data = dashboardService.getDashboardData(walletId, startDate, endDate, periodType);
        return ResponseEntity.ok(data);
    }
}
