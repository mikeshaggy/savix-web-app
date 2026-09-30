package com.mikeshaggy.backend.transfer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.transfer.dto.TransferResponse;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transfer.service.TransferService;
import com.mikeshaggy.backend.transfer.service.TransferSummaryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    private TransferSummaryService transferSummaryService;

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

    @Nested
    class Summary {

        @Test
        void defaultsToThreeCyclesAndSerializesContract() throws Exception {
            // given
            TransferSummaryResponse.CounterWalletFlow savings = new TransferSummaryResponse.CounterWalletFlow(
                    2, "FX_SAVINGS_WALLET", new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("500.00"));
            TransferSummaryResponse.CycleSummary current = new TransferSummaryResponse.CycleSummary(
                    LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 8), CycleState.OPEN,
                    new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("500.00"), List.of(savings));
            TransferSummaryResponse response = new TransferSummaryResponse(1, current, List.of(),
                    List.of(new TransferSummaryResponse.CounterWallet(2, "FX_SAVINGS_WALLET", new BigDecimal("460.01"))));
            when(transferSummaryService.getSummary(1, TEST_USER_ID, 3)).thenReturn(response);

            // when / then
            mockMvc.perform(get("/api/transfers/wallet/1/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.walletId").value(1))
                    .andExpect(jsonPath("$.currentCycle.start").value("2026-09-09"))
                    .andExpect(jsonPath("$.currentCycle.end").value("2026-10-08"))
                    .andExpect(jsonPath("$.currentCycle.state").value("OPEN"))
                    .andExpect(jsonPath("$.currentCycle.in").value(1000.00))
                    .andExpect(jsonPath("$.currentCycle.out").value(500.00))
                    .andExpect(jsonPath("$.currentCycle.net").value(500.00))
                    .andExpect(jsonPath("$.currentCycle.counterWallets[0].walletId").value(2))
                    .andExpect(jsonPath("$.currentCycle.counterWallets[0].walletName").value("FX_SAVINGS_WALLET"))
                    .andExpect(jsonPath("$.currentCycle.counterWallets[0].net").value(500.00))
                    .andExpect(jsonPath("$.cycles").isEmpty())
                    .andExpect(jsonPath("$.counterWallets[0].balance").value(460.01));
        }

        @Test
        void invalidCycles_returns400() throws Exception {
            // given
            when(transferSummaryService.getSummary(1, TEST_USER_ID, 0))
                    .thenThrow(new IllegalArgumentException("cycles must be between 1 and 24"));

            // when / then
            mockMvc.perform(get("/api/transfers/wallet/1/summary").param("cycles", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("cycles must be between 1 and 24"));
        }

        @Test
        void foreignWallet_returns404() throws Exception {
            // given
            when(transferSummaryService.getSummary(99, TEST_USER_ID, 3))
                    .thenThrow(new jakarta.persistence.EntityNotFoundException("Wallet not found with id: 99"));

            // when / then
            mockMvc.perform(get("/api/transfers/wallet/99/summary"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void nonNumericCycles_returns400() throws Exception {
            mockMvc.perform(get("/api/transfers/wallet/1/summary").param("cycles", "abc"))
                    .andExpect(status().isBadRequest());
        }
    }
}
