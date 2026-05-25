package com.mikeshaggy.backend.fixedpayment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.fixedpayment.dto.FixedPaymentResponse;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentCrudService;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

@WebMvcTest(FixedPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class FixedPaymentControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FixedPaymentCrudService fixedPaymentCrudService;

    @MockitoBean
    private FixedPaymentDashboardService fixedPaymentDashboardService;

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
    class CreateFixedPayment {

        @Test
        void happyPath_returns201WithBody() throws Exception {
            // given
            var response =
                    new FixedPaymentResponse(
                            1,
                            1,
                            "Main",
                            1,
                            "Rent",
                            null,
                            "Monthly Rent",
                            new BigDecimal("1000.00"),
                            LocalDate.of(2026, 3, 1),
                            Cycle.MONTHLY,
                            LocalDate.of(2026, 3, 1),
                            null,
                            null,
                            LocalDateTime.of(2026, 3, 11, 10, 0));
            when(fixedPaymentCrudService.createFixedPayment(any(), eq(TEST_USER_ID)))
                    .thenReturn(response);

            // when
            mockMvc
                    .perform(
                            post("/api/fixed-payments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {
                                                "walletId": 1,
                                                "categoryId": 1,
                                                "title": "Monthly Rent",
                                                "amount": 1000.00,
                                                "anchorDate": "2026-03-01",
                                                "cycle": "MONTHLY"
                                            }
                                            """))
                    // then
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.title").value("Monthly Rent"))
                    .andExpect(jsonPath("$.amount").value(1000.00))
                    .andExpect(jsonPath("$.cycle").value("MONTHLY"));
        }

        @Test
        void missingRequiredFields_returns400() throws Exception {
            // given
            // when
            mockMvc
                    .perform(
                            post("/api/fixed-payments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"notes": "incomplete"}
                                            """))
                    // then
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.details.walletId").exists())
                    .andExpect(jsonPath("$.details.title").exists())
                    .andExpect(jsonPath("$.details.amount").exists());
        }
    }
}
