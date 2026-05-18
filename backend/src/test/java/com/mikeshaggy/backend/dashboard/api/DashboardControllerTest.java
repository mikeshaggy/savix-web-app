package com.mikeshaggy.backend.dashboard.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.dashboard.dto.DashboardData;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.dto.SummaryDto;
import com.mikeshaggy.backend.dashboard.service.DashboardOrchestrator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardOrchestrator dashboardOrchestrator;

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

    @Nested
    class GetDashboard {

        @Test
        void happyPath_returns200WithDashboardData() throws Exception {
            // given
            var data =
                    new DashboardData(
                            new PeriodDto(
                                    LocalDate.of(2026, 3, 1),
                                    LocalDate.of(2026, 3, 31),
                                    LocalDate.of(2026, 3, 31),
                                    PeriodType.PAY_CYCLE),
                            new SummaryDto(
                                    new BigDecimal("5000"),
                                    new BigDecimal("3000"),
                                    new BigDecimal("2000"),
                                    new BigDecimal("40.0"),
                                    null,
                                    null,
                                    null),
                            List.of(),
                            "Main Wallet",
                            null);
            when(dashboardOrchestrator.getDashboardData(eq(TEST_USER_ID), eq(1), any(), any(), any()))
                    .thenReturn(data);

            // when
            mockMvc
                    .perform(get("/api/dashboard").param("walletId", "1"))
                    // then
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.walletName").value("Main Wallet"))
                    .andExpect(jsonPath("$.period.periodType").value("PAY_CYCLE"))
                    .andExpect(jsonPath("$.summary.income").value(5000))
                    .andExpect(jsonPath("$.summary.expenses").value(3000))
                    .andExpect(jsonPath("$.summary.saved").value(2000));
        }

        @Test
        void serviceThrowsIllegalArgument_returns400() throws Exception {
            // given
            when(dashboardOrchestrator.getDashboardData(eq(TEST_USER_ID), eq(1), any(), any(), any()))
                    .thenThrow(new IllegalArgumentException("Unsupported period type"));

            // when
            mockMvc
                    .perform(get("/api/dashboard").param("walletId", "1"))
                    // then
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Unsupported period type"));
        }
    }
}
