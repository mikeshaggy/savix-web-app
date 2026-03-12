package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.dashboard.dto.DashboardData;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentOccurrenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardOrchestrator {

    private final FixedPaymentOccurrenceService fixedPaymentOccurrenceService;
    private final DashboardService dashboardService;

    public DashboardData getDashboardData(UUID userId, Integer walletId,
                                          LocalDate startDate, LocalDate endDate,
                                          PeriodType periodType) {
        fixedPaymentOccurrenceService.prepareOccurrencesForDashboard(userId);

        return dashboardService.getDashboardData(userId, walletId, startDate, endDate, periodType);
    }
}
