package com.mikeshaggy.backend.insight.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightKind;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import com.mikeshaggy.backend.insight.dto.InsightAvailability;
import com.mikeshaggy.backend.insight.dto.InsightListResponse;
import com.mikeshaggy.backend.insight.dto.InsightResponse;
import com.mikeshaggy.backend.insight.service.InsightService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InsightController.class)
@AutoConfigureMockMvc(addFilters = false)
class InsightControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InsightService insightService;

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
    void list_returnsContract() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"impact\":300.00,\"transactionId\":42}");
        InsightResponse insight = new InsightResponse(17L, InsightType.ONE_OFF_SKEWING_FORECAST, InsightKind.NOTABLE,
                InsightSeverity.WARN, LocalDate.of(2026, 10, 9), "A one-off skews the forecast", "300.00 on 12 Oct", payload,
                InsightActionType.EXCLUDE_TRANSACTION, "42", Instant.parse("2026-10-22T10:00:00Z"));
        when(insightService.evaluate(1, TEST_USER_ID)).thenReturn(new InsightListResponse(1,
                InsightAvailability.AVAILABLE, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 11, 9),
                CycleState.OPEN, List.of(insight)));

        mockMvc.perform(get("/api/insights").param("walletId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(1))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.cycleStart").value("2026-10-09"))
                .andExpect(jsonPath("$.cycleEnd").value("2026-11-09"))
                .andExpect(jsonPath("$.cycleState").value("OPEN"))
                .andExpect(jsonPath("$.insights[0].id").value(17))
                .andExpect(jsonPath("$.insights[0].type").value("ONE_OFF_SKEWING_FORECAST"))
                .andExpect(jsonPath("$.insights[0].kind").value("NOTABLE"))
                .andExpect(jsonPath("$.insights[0].severity").value("WARN"))
                .andExpect(jsonPath("$.insights[0].cycleStart").value("2026-10-09"))
                .andExpect(jsonPath("$.insights[0].title").value("A one-off skews the forecast"))
                .andExpect(jsonPath("$.insights[0].description").value("300.00 on 12 Oct"))
                .andExpect(jsonPath("$.insights[0].payload.transactionId").value(42))
                .andExpect(jsonPath("$.insights[0].actionType").value("EXCLUDE_TRANSACTION"))
                .andExpect(jsonPath("$.insights[0].actionRef").value("42"))
                .andExpect(jsonPath("$.insights[0].createdAt").exists());
    }

    @Test
    void list_flagOff_returnsDisabledEmptyList() throws Exception {
        when(insightService.evaluate(1, TEST_USER_ID))
                .thenReturn(InsightListResponse.unavailable(1, InsightAvailability.FEATURE_DISABLED));

        mockMvc.perform(get("/api/insights").param("walletId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FEATURE_DISABLED"))
                .andExpect(jsonPath("$.cycleStart").doesNotExist())
                .andExpect(jsonPath("$.insights").isEmpty());
    }

    @Test
    void list_foreignWallet_is404() throws Exception {
        when(insightService.evaluate(9, TEST_USER_ID))
                .thenThrow(new EntityNotFoundException("Wallet not found with id: 9"));

        mockMvc.perform(get("/api/insights").param("walletId", "9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_withoutWalletId_is400() throws Exception {
        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isBadRequest());
        verify(insightService, never()).evaluate(any(), any());
    }

    @Test
    void list_nonNumericWalletId_is400() throws Exception {
        mockMvc.perform(get("/api/insights").param("walletId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dismiss_returns204_andPassesTheCurrentUser() throws Exception {
        doNothing().when(insightService).dismiss(17L, TEST_USER_ID);

        mockMvc.perform(delete("/api/insights/17"))
                .andExpect(status().isNoContent());
        verify(insightService).dismiss(17L, TEST_USER_ID);
    }

    @Test
    void dismiss_unknownOrForeignId_is404() throws Exception {
        doThrow(new EntityNotFoundException("Insight not found with id: 18"))
                .when(insightService).dismiss(18L, TEST_USER_ID);

        mockMvc.perform(delete("/api/insights/18"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void dismiss_nonNumericId_is400() throws Exception {
        mockMvc.perform(delete("/api/insights/abc"))
                .andExpect(status().isBadRequest());
    }
}
