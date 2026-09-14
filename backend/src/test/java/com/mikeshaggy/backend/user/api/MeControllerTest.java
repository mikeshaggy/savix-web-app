package com.mikeshaggy.backend.user.api;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.paycycle.WeekendShift;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.user.dto.MeFeaturesResponse;
import com.mikeshaggy.backend.user.dto.MeResponse;
import com.mikeshaggy.backend.user.dto.MeUpdateRequest;
import com.mikeshaggy.backend.user.dto.PaydayRuleRequest;
import com.mikeshaggy.backend.user.dto.PaydayRuleResponse;
import com.mikeshaggy.backend.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final MeFeaturesResponse NO_FEATURES = new MeFeaturesResponse(false, false, false, false);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

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
    void getCurrentUser_returnsFeaturesObject() throws Exception {
        when(userService.getUserById(TEST_USER_ID))
                .thenReturn(me("fx-user", null, null));

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("fx@example.com"))
                .andExpect(jsonPath("$.salaryWalletId").value((Object) null))
                .andExpect(jsonPath("$.paydayRule").value((Object) null))
                .andExpect(jsonPath("$.features.payCycleV2").value(false))
                .andExpect(jsonPath("$.features.forecastV2").value(false))
                .andExpect(jsonPath("$.features.dashboardV2").value(false))
                .andExpect(jsonPath("$.features.insightsV2").value(false))
                .andExpect(jsonPath("$.features.forecastV2Shadow").doesNotExist());
    }

    @Test
    void getCurrentUser_returnsSalaryWalletIdAndPaydayRule() throws Exception {
        when(userService.getUserById(TEST_USER_ID))
                .thenReturn(me("fx-user", 1, new PaydayRuleResponse(10, WeekendShift.PREVIOUS_BUSINESS_DAY)));

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryWalletId").value(1))
                .andExpect(jsonPath("$.paydayRule.dayOfMonth").value(10))
                .andExpect(jsonPath("$.paydayRule.weekendShift").value("PREVIOUS_BUSINESS_DAY"));
    }

    @Test
    void updateCurrentUser_returnsSameFeaturesShape() throws Exception {
        when(userService.updateUserById(eq(TEST_USER_ID), any(MeUpdateRequest.class)))
                .thenReturn(me("fx-user-renamed", null, null));

        mockMvc.perform(patch("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "fx-user-renamed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.username").value("fx-user-renamed"))
                .andExpect(jsonPath("$.features.payCycleV2").value(false))
                .andExpect(jsonPath("$.features.forecastV2").value(false))
                .andExpect(jsonPath("$.features.dashboardV2").value(false))
                .andExpect(jsonPath("$.features.insightsV2").value(false))
                .andExpect(jsonPath("$.features.forecastV2Shadow").doesNotExist());
    }

    @Test
    void updateCurrentUser_salaryWalletOnly_passesIdThroughAndReturnsIt() throws Exception {
        when(userService.updateUserById(eq(TEST_USER_ID), any(MeUpdateRequest.class)))
                .thenReturn(me("fx-user", 1, null));

        mockMvc.perform(patch("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "salaryWalletId": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryWalletId").value(1));

        ArgumentCaptor<MeUpdateRequest> captor = ArgumentCaptor.forClass(MeUpdateRequest.class);
        verify(userService).updateUserById(eq(TEST_USER_ID), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new MeUpdateRequest(null, 1));
    }

    @Test
    void updateCurrentUser_foreignWallet_returns404() throws Exception {
        when(userService.updateUserById(eq(TEST_USER_ID), any(MeUpdateRequest.class)))
                .thenThrow(new EntityNotFoundException("Wallet not found with id: 99"));

        mockMvc.perform(patch("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salaryWalletId\": 99}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCurrentUser_fundWallet_returns400() throws Exception {
        when(userService.updateUserById(eq(TEST_USER_ID), any(MeUpdateRequest.class)))
                .thenThrow(new IllegalArgumentException("Salary wallet cannot be a fund wallet."));

        mockMvc.perform(patch("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salaryWalletId\": 5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCurrentUser_usernameTooShort_returns400() throws Exception {
        mockMvc.perform(patch("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"x\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateUserById(any(), any());
    }

    @Test
    void setPaydayRule_returnsMeWithRule() throws Exception {
        when(userService.setPaydayRule(eq(TEST_USER_ID), any(PaydayRuleRequest.class)))
                .thenReturn(me("fx-user", 1, new PaydayRuleResponse(10, WeekendShift.NEXT_BUSINESS_DAY)));

        mockMvc.perform(put("/api/me/payday-rule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "dayOfMonth": 10,
                                    "weekendShift": "NEXT_BUSINESS_DAY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paydayRule.dayOfMonth").value(10))
                .andExpect(jsonPath("$.paydayRule.weekendShift").value("NEXT_BUSINESS_DAY"));

        ArgumentCaptor<PaydayRuleRequest> captor = ArgumentCaptor.forClass(PaydayRuleRequest.class);
        verify(userService).setPaydayRule(eq(TEST_USER_ID), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PaydayRuleRequest(10, WeekendShift.NEXT_BUSINESS_DAY));
    }

    @Test
    void setPaydayRule_dayOfMonthOutOfRange_returns400() throws Exception {
        mockMvc.perform(put("/api/me/payday-rule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dayOfMonth\": 32}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).setPaydayRule(any(), any());
    }

    @Test
    void setPaydayRule_missingDayOfMonth_returns400() throws Exception {
        mockMvc.perform(put("/api/me/payday-rule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekendShift\": \"NONE\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).setPaydayRule(any(), any());
    }

    @Test
    void deletePaydayRule_returnsMeWithoutRule() throws Exception {
        when(userService.deletePaydayRule(TEST_USER_ID)).thenReturn(me("fx-user", 1, null));

        mockMvc.perform(delete("/api/me/payday-rule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryWalletId").value(1))
                .andExpect(jsonPath("$.paydayRule").value((Object) null));

        verify(userService).deletePaydayRule(TEST_USER_ID);
    }

    private static MeResponse me(String username, Integer salaryWalletId, PaydayRuleResponse rule) {
        return new MeResponse(TEST_USER_ID, "fx@example.com", username, salaryWalletId, rule, NO_FEATURES);
    }
}
