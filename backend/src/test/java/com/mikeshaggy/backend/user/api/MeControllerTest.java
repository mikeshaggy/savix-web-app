package com.mikeshaggy.backend.user.api;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.user.dto.MeFeaturesResponse;
import com.mikeshaggy.backend.user.dto.MeResponse;
import com.mikeshaggy.backend.user.dto.MeUpdateRequest;
import com.mikeshaggy.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

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
                .thenReturn(new MeResponse(TEST_USER_ID, "fx@example.com", "fx-user",
                        new MeFeaturesResponse(false, false, false, false)));

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("fx@example.com"))
                .andExpect(jsonPath("$.features.payCycleV2").value(false))
                .andExpect(jsonPath("$.features.forecastV2").value(false))
                .andExpect(jsonPath("$.features.dashboardV2").value(false))
                .andExpect(jsonPath("$.features.insightsV2").value(false))
                .andExpect(jsonPath("$.features.forecastV2Shadow").doesNotExist());
    }

    @Test
    void updateCurrentUser_returnsSameFeaturesShape() throws Exception {
        when(userService.updateUserById(eq(TEST_USER_ID), any(MeUpdateRequest.class)))
                .thenReturn(new MeResponse(TEST_USER_ID, "fx@example.com", "fx-user-renamed",
                        new MeFeaturesResponse(false, false, false, false)));

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
}
