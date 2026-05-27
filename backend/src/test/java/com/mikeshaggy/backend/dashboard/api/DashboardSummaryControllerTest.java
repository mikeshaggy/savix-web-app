package com.mikeshaggy.backend.dashboard.api;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.dashboard.dto.DashboardCycleHealthDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardFixedPaymentsDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardHealthStatus;
import com.mikeshaggy.backend.dashboard.dto.DashboardKpisDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardMoneyKpiDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardPercentKpiDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardPeriodDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardPreviousCyclePreviewDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardSummaryDto;
import com.mikeshaggy.backend.dashboard.service.DashboardSummaryService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardSummaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardSummaryControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardSummaryService dashboardSummaryService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthCookieManager cookieManager;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(TEST_USER_ID);
    }

    @Test
    void defaultRequestReturnsDashboardSummary() throws Exception {
        DashboardSummaryDto response = summary();
        when(dashboardSummaryService.getSummary(
                eq(1), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE),
                isNull(), isNull(), isNull(), eq(com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)))
                .thenReturn(response);

        mockMvc.perform(get("/api/wallets/1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(1))
                .andExpect(jsonPath("$.walletName").value("Main"))
                .andExpect(jsonPath("$.period.type").value("PAY_CYCLE"))
                .andExpect(jsonPath("$.cycleHealth.status").value("ON_TRACK"))
                .andExpect(jsonPath("$.kpis.income.amount").value(5000.00))
                .andExpect(jsonPath("$.fixedPayments.totalCount").value(2));
    }

    @Test
    void queryParamsArePassedToService() throws Exception {
        DashboardSummaryDto response = summary();
        when(dashboardSummaryService.getSummary(
                eq(1), eq(TEST_USER_ID), eq(PeriodType.MONTHLY),
                eq(LocalDate.of(2026, 5, 1)), isNull(), eq(LocalDate.of(2026, 5, 15)),
                eq(com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode.ALL)))
                .thenReturn(response);

        mockMvc.perform(get("/api/wallets/1/dashboard/summary")
                        .queryParam("periodType", "MONTHLY")
                        .queryParam("startDate", "2026-05-01")
                        .queryParam("asOfDate", "2026-05-15")
                        .queryParam("categoryMode", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(1));
    }

    @Test
    void inaccessibleWalletReturns404() throws Exception {
        when(dashboardSummaryService.getSummary(
                eq(999), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE),
                isNull(), isNull(), isNull(),
                eq(com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)))
                .thenThrow(new EntityNotFoundException("Wallet not found"));

        mockMvc.perform(get("/api/wallets/999/dashboard/summary"))
                .andExpect(status().isNotFound());
    }

    private DashboardSummaryDto summary() {
        return new DashboardSummaryDto(
                1,
                "Main",
                new DashboardPeriodDto(
                        PeriodType.PAY_CYCLE,
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 5, 26),
                        LocalDate.of(2026, 5, 26),
                        31,
                        26,
                        5,
                        true,
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 4, 26)),
                new DashboardCycleHealthDto(
                        DashboardHealthStatus.ON_TRACK,
                        new BigDecimal("2500.00"),
                        new BigDecimal("620.00"),
                        new BigDecimal("740.00"),
                        new BigDecimal("120.00"),
                        new BigDecimal("8.50"),
                        true,
                        null),
                new DashboardKpisDto(
                        new DashboardMoneyKpiDto(new BigDecimal("5000.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                        new DashboardMoneyKpiDto(new BigDecimal("3100.00"), new BigDecimal("240.00"), new BigDecimal("8.39")),
                        new DashboardMoneyKpiDto(new BigDecimal("1900.00"), new BigDecimal("-240.00"), new BigDecimal("-11.21")),
                        new DashboardPercentKpiDto(new BigDecimal("38.00"), new BigDecimal("-4.80"))),
                new DashboardFixedPaymentsDto(
                        new BigDecimal("1800.00"),
                        new BigDecimal("1200.00"),
                        new BigDecimal("600.00"),
                        BigDecimal.ZERO,
                        1,
                        2,
                        null,
                        List.of(),
                        new BigDecimal("1900.00"),
                        false,
                        null),
                List.of(),
                List.of(),
                new DashboardPreviousCyclePreviewDto(
                        new BigDecimal("240.00"),
                        new BigDecimal("8.39"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("-240.00"),
                        new BigDecimal("-11.21"),
                        new BigDecimal("-4.80"),
                        List.of()));
    }
}
