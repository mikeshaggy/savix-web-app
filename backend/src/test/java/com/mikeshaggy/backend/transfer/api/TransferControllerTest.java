package com.mikeshaggy.backend.transfer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.transfer.dto.TransferResponse;
import com.mikeshaggy.backend.transfer.service.TransferService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransferController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

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
    class CreateTransfer {

        @Test
        void happyPath_returns201WithBody() throws Exception {
            // given
            var response =
                    new TransferResponse(
                            1L,
                            1,
                            "Main",
                            2,
                            "Savings",
                            new BigDecimal("100.00"),
                            LocalDate.of(2026, 3, 11),
                            null,
                            Instant.parse("2026-03-11T10:00:00Z"));
            when(transferService.createTransfer(any(), eq(TEST_USER_ID))).thenReturn(response);

            // when
            mockMvc
                    .perform(
                            post("/api/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {
                                                "fromWalletId": 1,
                                                "toWalletId": 2,
                                                "amount": 100.00,
                                                "transferDate": "2026-03-11"
                                            }
                                            """))
                    // then
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.fromWalletName").value("Main"))
                    .andExpect(jsonPath("$.toWalletName").value("Savings"))
                    .andExpect(jsonPath("$.amount").value(100.00));
        }

        @Test
        void missingRequiredFields_returns400() throws Exception {
            // given
            // when
            mockMvc
                    .perform(
                            post("/api/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"notes": "incomplete"}
                                            """))
                    // then
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.details.fromWalletId").exists())
                    .andExpect(jsonPath("$.details.toWalletId").exists())
                    .andExpect(jsonPath("$.details.amount").exists());
        }

        @Test
        void selfTransfer_returns400() throws Exception {
            // given
            when(transferService.createTransfer(any(), eq(TEST_USER_ID)))
                    .thenThrow(new IllegalArgumentException("Cannot transfer to the same wallet"));

            // when
            mockMvc
                    .perform(
                            post("/api/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {
                                                "fromWalletId": 1,
                                                "toWalletId": 1,
                                                "amount": 100.00,
                                                "transferDate": "2026-03-11"
                                            }
                                            """))
                    // then
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Cannot transfer to the same wallet"));
        }
    }
}
