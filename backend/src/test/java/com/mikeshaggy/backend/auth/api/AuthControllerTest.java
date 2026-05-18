package com.mikeshaggy.backend.auth.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.auth.dto.LoginResult;
import com.mikeshaggy.backend.auth.dto.TokenPair;
import com.mikeshaggy.backend.auth.exception.AuthException;
import com.mikeshaggy.backend.auth.exception.RateLimitException;
import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.service.PasswordResetService;
import com.mikeshaggy.backend.auth.service.RegistrationService;
import com.mikeshaggy.backend.auth.service.SessionService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private AuthCookieManager cookieManager;

    @MockitoBean
    private JwtService jwtService;

    @Nested
    class Login {

        @Test
        void happyPath_returns200() throws Exception {
            // given
            when(sessionService.login(any(), any()))
                    .thenReturn(new LoginResult(new TokenPair("access", "refresh")));

            // when
            mockMvc
                    .perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email": "user@example.com", "password": "SecurePassword123!"}
                                            """))
                    // then
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Login successful"));
        }

        @Test
        void blankEmail_returns400WithValidationErrors() throws Exception {
            // given
            // when
            mockMvc
                    .perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email": "", "password": "SecurePassword123!"}
                                            """))
                    // then
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.details.email").exists());
        }

        @Test
        void authException_returns401() throws Exception {
            // given
            when(sessionService.login(any(), any())).thenThrow(new AuthException("Invalid credentials"));

            // when
            mockMvc
                    .perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email": "user@example.com", "password": "WrongPassword123!"}
                                            """))
                    // then
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid credentials"));
        }

        @Test
        void rateLimitException_returns429() throws Exception {
            // given
            when(sessionService.login(any(), any()))
                    .thenThrow(new RateLimitException("Too many login attempts"));

            // when
            mockMvc
                    .perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email": "user@example.com", "password": "SecurePassword123!"}
                                            """))
                    // then
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.message").value("Too many login attempts"));
        }
    }
}
