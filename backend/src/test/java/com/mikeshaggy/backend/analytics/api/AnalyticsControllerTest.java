package com.mikeshaggy.backend.analytics.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.analytics.comparison.BaselineComparisonDto;
import com.mikeshaggy.backend.analytics.breakdown.CategoryBreakdownDto;
import com.mikeshaggy.backend.analytics.breakdown.CategoryBreakdownItemDto;
import com.mikeshaggy.backend.analytics.daily.HeatmapDayDto;
import com.mikeshaggy.backend.analytics.daily.HeatmapResponseDto;
import com.mikeshaggy.backend.analytics.breakdown.ImportanceBreakdownDto;
import com.mikeshaggy.backend.analytics.breakdown.ImportanceBreakdownItemDto;
import com.mikeshaggy.backend.analytics.insight.InsightDto;
import com.mikeshaggy.backend.analytics.insight.InsightResponseDto;
import com.mikeshaggy.backend.analytics.insight.InsightSeverity;
import com.mikeshaggy.backend.analytics.insight.InsightType;
import com.mikeshaggy.backend.analytics.overview.PeriodOverviewDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.comparison.BaselineService;
import com.mikeshaggy.backend.analytics.breakdown.CategoryBreakdownService;
import com.mikeshaggy.backend.analytics.daily.HeatmapService;
import com.mikeshaggy.backend.analytics.breakdown.ImportanceBreakdownService;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.analytics.overview.AnalyticsSummaryService;
import com.mikeshaggy.backend.analytics.overview.PeriodOverviewService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import jakarta.persistence.EntityNotFoundException;
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

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PeriodService periodService;

    @MockitoBean
    private AnalyticsSummaryService analyticsSummaryService;

    @MockitoBean
    private PeriodOverviewService periodOverviewService;

    @MockitoBean
    private SpendingProjectionService spendingProjectionService;

    @MockitoBean
    private CategoryBreakdownService categoryBreakdownService;

    @MockitoBean
    private ImportanceBreakdownService importanceBreakdownService;

    @MockitoBean
    private HeatmapService heatmapService;

    @MockitoBean
    private BaselineService baselineService;

    @MockitoBean
    private InsightEngine insightEngine;

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
    class PeriodOverview {

        @Test
        void defaultPeriodType_returns200() throws Exception {
            PeriodOverviewDto response = new PeriodOverviewDto(
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    1,
                    new BigDecimal("5000.00"),
                    new BigDecimal("3200.00"),
                    new BigDecimal("1800.00"),
                    new BigDecimal("36.00"),
                    47L,
                    new BigDecimal("103.23"),
                    31,
                    31);
            when(periodOverviewService.getPeriodOverview(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE), isNull(), isNull()))
                    .thenReturn(response);

            mockMvc.perform(get("/api/wallets/1/analytics/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.income").value(5000.00))
                    .andExpect(jsonPath("$.expenses").value(3200.00))
                    .andExpect(jsonPath("$.balance").value(1800.00))
                    .andExpect(jsonPath("$.savingsRate").value(36.00))
                    .andExpect(jsonPath("$.transactionCount").value(47))
                    .andExpect(jsonPath("$.avgDailySpending").value(103.23))
                    .andExpect(jsonPath("$.daysInPeriod").value(31))
                    .andExpect(jsonPath("$.daysElapsed").value(31));
        }

        @Test
        void monthlyPeriodType_returns200() throws Exception {
            PeriodOverviewDto response = new PeriodOverviewDto(
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    1,
                    new BigDecimal("5000.00"),
                    new BigDecimal("3200.00"),
                    new BigDecimal("1800.00"),
                    new BigDecimal("36.00"),
                    47L,
                    new BigDecimal("103.23"),
                    31,
                    31);
            when(periodOverviewService.getPeriodOverview(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.MONTHLY),
                    eq(LocalDate.of(2026, 3, 1)), isNull()))
                    .thenReturn(response);

            mockMvc.perform(get("/api/wallets/1/analytics/overview")
                            .queryParam("periodType", "MONTHLY")
                            .queryParam("startDate", "2026-03-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value("2026-03-01"))
                    .andExpect(jsonPath("$.endDate").value("2026-03-31"));
        }

        @Test
        void futurePeriod_returns400() throws Exception {
            when(periodOverviewService.getPeriodOverview(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.MONTHLY),
                    eq(LocalDate.of(2099, 1, 1)), isNull()))
                    .thenThrow(new IllegalArgumentException("period must not be in the future"));

            mockMvc.perform(get("/api/wallets/1/analytics/overview")
                            .queryParam("periodType", "MONTHLY")
                            .queryParam("startDate", "2099-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("period must not be in the future"));
        }

        @Test
        void foreignWallet_returns404() throws Exception {
            when(periodOverviewService.getPeriodOverview(
                    eq(77), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE), isNull(), isNull()))
                    .thenThrow(new EntityNotFoundException("Wallet not found with id: 77"));

            mockMvc.perform(get("/api/wallets/77/analytics/overview"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Wallet not found with id: 77"));
        }
    }

    @Nested
    class SpendingProjections {

        @Test
        void defaultProjectionsRequest_returns200() throws Exception {
            SpendingProjectionDto response = projection(PeriodType.PAY_CYCLE);
            when(spendingProjectionService.getSpendingProjection(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE), isNull(), isNull()))
                    .thenReturn(response);

            mockMvc.perform(get("/api/wallets/1/analytics/projections"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodType").value("PAY_CYCLE"))
                    .andExpect(jsonPath("$.safeToSpendToday").value(1000.00));
        }

        @Test
        void payCycleProjectionsValidRequest_returns200() throws Exception {
            when(spendingProjectionService.getSpendingProjection(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE), isNull(), isNull()))
                    .thenReturn(projection(PeriodType.PAY_CYCLE));

            mockMvc.perform(get("/api/wallets/1/analytics/projections")
                            .queryParam("periodType", "PAY_CYCLE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodType").value("PAY_CYCLE"));
        }

        @Test
        void lastPayCycleProjectionsValidRequest_returns200() throws Exception {
            when(spendingProjectionService.getSpendingProjection(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.LAST_PAY_CYCLE), isNull(), isNull()))
                    .thenReturn(projection(PeriodType.LAST_PAY_CYCLE));

            mockMvc.perform(get("/api/wallets/1/analytics/projections")
                            .queryParam("periodType", "LAST_PAY_CYCLE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodType").value("LAST_PAY_CYCLE"));
        }

        @Test
        void monthlyProjectionsValidRequest_returns200() throws Exception {
            when(spendingProjectionService.getSpendingProjection(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.MONTHLY), isNull(), isNull()))
                    .thenReturn(projection(PeriodType.MONTHLY));

            mockMvc.perform(get("/api/wallets/1/analytics/projections")
                            .queryParam("periodType", "MONTHLY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodType").value("MONTHLY"));
        }

        @Test
        void customProjectionsValidRequest_returns200() throws Exception {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);
            when(spendingProjectionService.getSpendingProjection(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM), eq(start), eq(end)))
                    .thenReturn(projection(PeriodType.CUSTOM));

            mockMvc.perform(get("/api/wallets/1/analytics/projections")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2026-03-01")
                            .queryParam("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodType").value("CUSTOM"));
        }

        @Test
        void invalidPeriodType_returns400() throws Exception {
            mockMvc.perform(get("/api/wallets/1/analytics/projections")
                            .queryParam("periodType", "WEEKLY"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void invalidStartDateFormat_returns400() throws Exception {
            mockMvc.perform(get("/api/wallets/1/analytics/projections")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "03-01-2026")
                            .queryParam("endDate", "2026-03-31"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void customMissingStartDateOrEndDate_returns400() throws Exception {
            when(spendingProjectionService.getSpendingProjection(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM),
                    eq(LocalDate.of(2026, 3, 1)), isNull()))
                    .thenThrow(new IllegalArgumentException("Both startDate and endDate are required for CUSTOM period type"));

            mockMvc.perform(get("/api/wallets/1/analytics/projections")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2026-03-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Both startDate and endDate are required for CUSTOM period type"));
        }

        @Test
        void customStartDateAfterEndDate_returns400() throws Exception {
            LocalDate start = LocalDate.of(2026, 3, 31);
            LocalDate end = LocalDate.of(2026, 3, 1);
            when(spendingProjectionService.getSpendingProjection(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM), eq(start), eq(end)))
                    .thenThrow(new IllegalArgumentException("startDate (2026-03-31) must not be after endDate (2026-03-01)"));

            mockMvc.perform(get("/api/wallets/1/analytics/projections")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2026-03-31")
                            .queryParam("endDate", "2026-03-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("startDate (2026-03-31) must not be after endDate (2026-03-01)"));
        }

        @Test
        void futureOnlyPeriod_returns400() throws Exception {
            LocalDate start = LocalDate.of(2099, 1, 1);
            LocalDate end = LocalDate.of(2099, 1, 31);
            when(spendingProjectionService.getSpendingProjection(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM), eq(start), eq(end)))
                    .thenThrow(new IllegalArgumentException("period must not be in the future"));

            mockMvc.perform(get("/api/wallets/1/analytics/projections")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2099-01-01")
                            .queryParam("endDate", "2099-01-31"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("period must not be in the future"));
        }

        @Test
        void foreignWalletBehaviorFollowsProjectConvention_returns404() throws Exception {
            when(spendingProjectionService.getSpendingProjection(
                    eq(77), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE), isNull(), isNull()))
                    .thenThrow(new EntityNotFoundException("Wallet not found with id: 77"));

            mockMvc.perform(get("/api/wallets/77/analytics/projections"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Wallet not found with id: 77"));
        }
    }

    @Nested
    class CategoryBreakdown {

        @Test
        void defaultCategoryBreakdownRequest_returns200() throws Exception {
            when(categoryBreakdownService.getCategoryBreakdown(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE), isNull(), isNull()))
                    .thenReturn(categoryBreakdown(PeriodType.PAY_CYCLE));

            mockMvc.perform(get("/api/wallets/1/analytics/category-breakdown"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodType").value("PAY_CYCLE"))
                    .andExpect(jsonPath("$.totalExpenses").value(3200.00))
                    .andExpect(jsonPath("$.categories[0].categoryId").value(12))
                    .andExpect(jsonPath("$.categories[0].share").value(25.63))
                    .andExpect(jsonPath("$.categories[0].transactionCount").value(14));
        }

        @Test
        void categoryBreakdownAcceptsPeriodParamsAndCurrentUser() throws Exception {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);
            when(categoryBreakdownService.getCategoryBreakdown(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM), eq(start), eq(end)))
                    .thenReturn(categoryBreakdown(PeriodType.CUSTOM));

            mockMvc.perform(get("/api/wallets/1/analytics/category-breakdown")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2026-03-01")
                            .queryParam("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodType").value("CUSTOM"))
                    .andExpect(jsonPath("$.startDate").value("2026-03-01"))
                    .andExpect(jsonPath("$.endDate").value("2026-03-31"));
        }
    }

    @Nested
    class ImportanceBreakdown {

        @Test
        void defaultImportanceBreakdownRequest_returns200() throws Exception {
            when(importanceBreakdownService.getImportanceBreakdown(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE), isNull(), isNull()))
                    .thenReturn(importanceBreakdown(PeriodType.PAY_CYCLE));

            mockMvc.perform(get("/api/wallets/1/analytics/importance-breakdown"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodType").value("PAY_CYCLE"))
                    .andExpect(jsonPath("$.totalExpenses").value(3200.00))
                    .andExpect(jsonPath("$.breakdown[0].importance").value("ESSENTIAL"))
                    .andExpect(jsonPath("$.breakdown[0].share").value(37.50))
                    .andExpect(jsonPath("$.breakdown[0].count").value(5));
        }

        @Test
        void importanceBreakdownAcceptsPeriodParamsAndCurrentUser() throws Exception {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);
            when(importanceBreakdownService.getImportanceBreakdown(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM), eq(start), eq(end)))
                    .thenReturn(importanceBreakdown(PeriodType.CUSTOM));

            mockMvc.perform(get("/api/wallets/1/analytics/importance-breakdown")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2026-03-01")
                            .queryParam("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodType").value("CUSTOM"))
                    .andExpect(jsonPath("$.startDate").value("2026-03-01"))
                    .andExpect(jsonPath("$.endDate").value("2026-03-31"));
        }
    }

    @Nested
    class Heatmap {

        @Test
        void defaultPeriodTypeReturns200() throws Exception {
            when(heatmapService.getHeatmap(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE), isNull(), isNull()))
                    .thenReturn(new HeatmapResponseDto(
                            LocalDate.of(2026, 3, 1),
                            LocalDate.of(2026, 3, 31),
                            List.of(new HeatmapDayDto(
                                    LocalDate.of(2026, 3, 1),
                                    new BigDecimal("45.00"),
                                    2L,
                                    List.of())),
                            new BigDecimal("45.00")));

            mockMvc.perform(get("/api/wallets/1/analytics/heatmap"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value("2026-03-01"))
                    .andExpect(jsonPath("$.endDate").value("2026-03-31"))
                    .andExpect(jsonPath("$.days[0].date").value("2026-03-01"))
                    .andExpect(jsonPath("$.days[0].total").value(45.00))
                    .andExpect(jsonPath("$.days[0].transactions").value(2))
                    .andExpect(jsonPath("$.maxDayTotal").value(45.00));
        }

        @Test
        void customDateRangeReturns200() throws Exception {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);
            when(heatmapService.getHeatmap(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM), eq(start), eq(end)))
                    .thenReturn(new HeatmapResponseDto(start, end, List.of(), BigDecimal.ZERO));

            mockMvc.perform(get("/api/wallets/1/analytics/heatmap")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2026-03-01")
                            .queryParam("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value("2026-03-01"))
                    .andExpect(jsonPath("$.endDate").value("2026-03-31"));
        }

        @Test
        void invalidPeriodTypeReturns400() throws Exception {
            mockMvc.perform(get("/api/wallets/1/analytics/heatmap")
                            .queryParam("periodType", "WEEKLY"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Baseline {

        @Test
        void defaultPeriodTypeReturns200() throws Exception {
            when(baselineService.getBaseline(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE), isNull(), isNull()))
                    .thenReturn(baseline());

            mockMvc.perform(get("/api/wallets/1/analytics/baseline"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodStart").value("2026-03-01"))
                    .andExpect(jsonPath("$.periodEnd").value("2026-03-31"))
                    .andExpect(jsonPath("$.compareStart").value("2026-02-01"))
                    .andExpect(jsonPath("$.compareEnd").value("2026-02-28"))
                    .andExpect(jsonPath("$.baselineIncome").value(4800.00))
                    .andExpect(jsonPath("$.currentExpenses").value(3200.00))
                    .andExpect(jsonPath("$.incomeDeltaPercent").value(4.17))
                    .andExpect(jsonPath("$.incomeDeltaDisplay").value("+4.17%"))
                    .andExpect(jsonPath("$.incomeDeltaAvailable").value(true));
        }

        @Test
        void customPeriodReturns200() throws Exception {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);
            when(baselineService.getBaseline(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM), eq(start), eq(end)))
                    .thenReturn(baseline());

            mockMvc.perform(get("/api/wallets/1/analytics/baseline")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2026-03-01")
                            .queryParam("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodStart").value("2026-03-01"));
        }

        @Test
        void invalidPeriodTypeReturns400() throws Exception {
            mockMvc.perform(get("/api/wallets/1/analytics/baseline")
                            .queryParam("periodType", "WEEKLY"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void serviceErrorPropagatesAs400() throws Exception {
            when(baselineService.getBaseline(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM), any(), any()))
                    .thenThrow(new IllegalArgumentException("Both startDate and endDate are required for CUSTOM period type"));

            mockMvc.perform(get("/api/wallets/1/analytics/baseline")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2026-03-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Both startDate and endDate are required for CUSTOM period type"));
        }
    }

    @Nested
    class Insights {

        @Test
        void defaultPeriodTypeReturnsInsights() throws Exception {
            InsightResponseDto response = new InsightResponseDto(
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    List.of(new InsightDto(
                            InsightType.CATEGORY_SPIKE,
                            InsightSeverity.WARN,
                            "Dining up 55%",
                            "You spent 620 PLN on Dining, compared to the previous period (400 PLN).",
                            7,
                            new BigDecimal("620.00"))));
            when(insightEngine.getInsights(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.PAY_CYCLE), isNull(), isNull()))
                    .thenReturn(response);

            mockMvc.perform(get("/api/wallets/1/analytics/insights"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodStart").value("2026-03-01"))
                    .andExpect(jsonPath("$.periodEnd").value("2026-03-31"))
                    .andExpect(jsonPath("$.insights[0].type").value("CATEGORY_SPIKE"))
                    .andExpect(jsonPath("$.insights[0].severity").value("WARN"))
                    .andExpect(jsonPath("$.insights[0].relatedCategoryId").value(7))
                    .andExpect(jsonPath("$.insights[0].relatedAmount").value(620.00));
        }

        @Test
        void customPeriodReturns200() throws Exception {
            LocalDate start = LocalDate.of(2026, 3, 1);
            LocalDate end = LocalDate.of(2026, 3, 31);
            InsightResponseDto response = new InsightResponseDto(start, end, List.of());
            when(insightEngine.getInsights(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM), eq(start), eq(end)))
                    .thenReturn(response);

            mockMvc.perform(get("/api/wallets/1/analytics/insights")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2026-03-01")
                            .queryParam("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.insights").isArray());
        }

        @Test
        void invalidPeriodTypeReturns400() throws Exception {
            mockMvc.perform(get("/api/wallets/1/analytics/insights")
                            .queryParam("periodType", "WEEKLY"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void serviceErrorPropagatesAs400() throws Exception {
            when(insightEngine.getInsights(
                    eq(1), eq(TEST_USER_ID), eq(PeriodType.CUSTOM), any(), any()))
                    .thenThrow(new IllegalArgumentException("period must not be in the future"));

            mockMvc.perform(get("/api/wallets/1/analytics/insights")
                            .queryParam("periodType", "CUSTOM")
                            .queryParam("startDate", "2099-01-01")
                            .queryParam("endDate", "2099-01-31"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("period must not be in the future"));
        }
    }

    private SpendingProjectionDto projection(PeriodType periodType) {
        return new SpendingProjectionDto(
                periodType,
                "Projection",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                31,
                10,
                21,
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("1850.00"),
                new BigDecimal("185.00"),
                new BigDecimal("5735.00"),
                new BigDecimal("-735.00"),
                new BigDecimal("350.00"),
                new BigDecimal("1000.00"),
                true,
                null);
    }

    private CategoryBreakdownDto categoryBreakdown(PeriodType periodType) {
        return new CategoryBreakdownDto(
                periodType,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                new BigDecimal("3200.00"),
                List.of(new CategoryBreakdownItemDto(
                        12,
                        "Groceries",
                        "🛒",
                        new BigDecimal("820.00"),
                        new BigDecimal("25.63"),
                        14L)));
    }

    private ImportanceBreakdownDto importanceBreakdown(PeriodType periodType) {
        return new ImportanceBreakdownDto(
                periodType,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                new BigDecimal("3200.00"),
                List.of(new ImportanceBreakdownItemDto(
                        Importance.ESSENTIAL,
                        new BigDecimal("1200.00"),
                        new BigDecimal("37.50"),
                        5L)));
    }

    private BaselineComparisonDto baseline() {
        return new BaselineComparisonDto(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                new BigDecimal("4800.00"),
                new BigDecimal("2900.00"),
                new BigDecimal("39.58"),
                new BigDecimal("5000.00"),
                new BigDecimal("3200.00"),
                new BigDecimal("36.00"),
                new BigDecimal("4.17"),
                "+4.17%",
                true,
                new BigDecimal("10.34"),
                "+10.34%",
                true,
                new BigDecimal("-9.05"),
                "-9.05%",
                true,
                List.of());
    }
}
