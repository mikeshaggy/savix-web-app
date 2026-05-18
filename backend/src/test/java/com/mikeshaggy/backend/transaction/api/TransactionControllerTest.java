package com.mikeshaggy.backend.transaction.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.dto.TransactionResponse;
import com.mikeshaggy.backend.transaction.service.TransactionOrchestrator;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import jakarta.persistence.EntityNotFoundException;
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

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private TransactionOrchestrator transactionOrchestrator;

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
    class CreateTransaction {

        @Test
        void happyPath_returns201WithBody() throws Exception {
            // given
            var response =
                    new TransactionResponse(
                            1L,
                            1,
                            "Main Wallet",
                            1,
                            "Groceries",
                            CategoryType.EXPENSE,
                            "🛒",
                            "Weekly groceries",
                            new BigDecimal("42.50"),
                            LocalDate.of(2026, 3, 11),
                            null,
                            Importance.ESSENTIAL,
                            Instant.parse("2026-03-11T10:00:00Z"));
            when(transactionOrchestrator.createTransaction(any(), eq(TEST_USER_ID))).thenReturn(response);

            // when
            mockMvc
                    .perform(
                            post("/api/transactions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {
                                                "walletId": 1,
                                                "categoryId": 1,
                                                "title": "Weekly groceries",
                                                "amount": 42.50,
                                                "transactionDate": "2026-03-11",
                                                "importance": "ESSENTIAL"
                                            }
                                            """))
                    // then
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.title").value("Weekly groceries"))
                    .andExpect(jsonPath("$.amount").value(42.50))
                    .andExpect(jsonPath("$.categoryType").value("EXPENSE"));
        }

        @Test
        void missingRequiredFields_returns400() throws Exception {
            // given
            // when
            mockMvc
                    .perform(
                            post("/api/transactions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"notes": "incomplete request"}
                                            """))
                    // then
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.details.walletId").exists())
                    .andExpect(jsonPath("$.details.title").exists())
                    .andExpect(jsonPath("$.details.amount").exists());
        }

        @Test
        void entityNotFound_returns404() throws Exception {
            // given
            when(transactionOrchestrator.createTransaction(any(), eq(TEST_USER_ID)))
                    .thenThrow(new EntityNotFoundException("Wallet not found"));

            // when
            mockMvc
                    .perform(
                            post("/api/transactions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {
                                                "walletId": 999,
                                                "categoryId": 1,
                                                "title": "Test",
                                                "amount": 10.00,
                                                "transactionDate": "2026-03-11"
                                            }
                                            """))
                    // then
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value("Wallet not found"));
        }
    }
}
