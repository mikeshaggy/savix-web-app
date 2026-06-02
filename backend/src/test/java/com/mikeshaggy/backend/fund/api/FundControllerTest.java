package com.mikeshaggy.backend.fund.api;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.exception.ConflictException;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.fund.domain.FundStatus;
import com.mikeshaggy.backend.fund.dto.*;
import com.mikeshaggy.backend.fund.service.FundService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FundController.class)
@AutoConfigureMockMvc(addFilters = false)
class FundControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FundService fundService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthCookieManager cookieManager;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    private FundResponse sampleFundResponse(Long id) {
        return new FundResponse(
                id, "Vacation", "Holiday", new BigDecimal("5000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5000.00"),
                false, "PLN", FundStatus.ACTIVE, "✈️", "#6366f1",
                null, null,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T10:00:00Z")
        );
    }

    @Nested
    class GetAll {

        @Test
        void returns200WithFundList() throws Exception {
            when(fundService.getFundsForUser(USER_ID)).thenReturn(List.of(sampleFundResponse(1L)));

            mockMvc.perform(get("/api/funds"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].name").value("Vacation"))
                    .andExpect(jsonPath("$[0].status").value("ACTIVE"));
        }
    }

    @Nested
    class GetById {

        @Test
        void returns200ForOwnedFund() throws Exception {
            when(fundService.getFundById(1L, USER_ID)).thenReturn(sampleFundResponse(1L));

            mockMvc.perform(get("/api/funds/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Vacation"));
        }

        @Test
        void returns404ForWrongUser() throws Exception {
            when(fundService.getFundById(99L, USER_ID))
                    .thenThrow(new EntityNotFoundException("Fund not found with id: 99"));

            mockMvc.perform(get("/api/funds/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetSummary {

        @Test
        void returns200WithSummary() throws Exception {
            FundsSummaryDto summary = new FundsSummaryDto(
                    2, new BigDecimal("5000.00"), new BigDecimal("20000.00"),
                    new BigDecimal("25.00"), List.of(), List.of());
            when(fundService.getFundsSummary(USER_ID)).thenReturn(summary);

            mockMvc.perform(get("/api/funds/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeFundsCount").value(2))
                    .andExpect(jsonPath("$.totalSaved").value(5000.00));
        }
    }

    @Nested
    class Create {

        @Test
        void returns201WithCreatedFund() throws Exception {
            when(fundService.createFund(any(), eq(USER_ID))).thenReturn(sampleFundResponse(1L));

            mockMvc.perform(post("/api/funds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Vacation",
                                      "targetAmount": 5000.00
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Vacation"));
        }

        @Test
        void missingName_returns400() throws Exception {
            mockMvc.perform(post("/api/funds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "targetAmount": 5000.00
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void missingTargetAmount_returns400() throws Exception {
            mockMvc.perform(post("/api/funds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Vacation"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void duplicateActiveName_returns409() throws Exception {
            when(fundService.createFund(any(), eq(USER_ID)))
                    .thenThrow(new ConflictException("An active fund named 'Vacation' already exists."));

            mockMvc.perform(post("/api/funds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Vacation",
                                      "targetAmount": 5000.00
                                    }
                                    """))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class Update {

        @Test
        void returns200WithUpdatedFund() throws Exception {
            when(fundService.updateFund(eq(1L), any(), eq(USER_ID))).thenReturn(sampleFundResponse(1L));

            mockMvc.perform(patch("/api/funds/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Updated description"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        void archivedFund_returns409() throws Exception {
            when(fundService.updateFund(eq(1L), any(), eq(USER_ID)))
                    .thenThrow(new ConflictException("Only active funds can be updated."));

            mockMvc.perform(patch("/api/funds/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Updated"
                                    }
                                    """))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class Archive {

        @Test
        void returns200WithArchivedFund() throws Exception {
            FundResponse archived = new FundResponse(
                    1L, "Vacation", null, new BigDecimal("5000.00"),
                    BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5000.00"),
                    false, "PLN", FundStatus.ARCHIVED, null, null,
                    null, null,
                    Instant.parse("2026-06-01T10:00:00Z"),
                    Instant.parse("2026-06-01T10:00:00Z")
            );
            when(fundService.archiveFund(eq(1L), any(), eq(USER_ID))).thenReturn(archived);

            mockMvc.perform(post("/api/funds/1/archive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "returnRemainingBalance": false
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ARCHIVED"));
        }

        @Test
        void balanceDecisionMissing_returns400() throws Exception {
            when(fundService.archiveFund(eq(1L), any(), eq(USER_ID)))
                    .thenThrow(new IllegalArgumentException("Please decide what to do with the remaining balance."));

            mockMvc.perform(post("/api/funds/1/archive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Deposit {

        @Test
        void returns200WithUpdatedFund() throws Exception {
            FundResponse updated = new FundResponse(
                    1L, "Vacation", null, new BigDecimal("5000.00"),
                    new BigDecimal("1000.00"), new BigDecimal("20.00"), new BigDecimal("4000.00"),
                    false, "PLN", FundStatus.ACTIVE, null, null,
                    null, null,
                    Instant.parse("2026-06-01T10:00:00Z"),
                    Instant.parse("2026-06-01T10:00:00Z")
            );
            when(fundService.depositToFund(eq(1L), any(), eq(USER_ID))).thenReturn(updated);

            mockMvc.perform(post("/api/funds/1/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "amount": 1000.00,
                                      "sourceWalletId": 1,
                                      "date": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentAmount").value(1000.00))
                    .andExpect(jsonPath("$.progressPercent").value(20.00))
                    .andExpect(jsonPath("$.remainingAmount").value(4000.00))
                    .andExpect(jsonPath("$.isTargetReached").value(false));
        }

        @Test
        void missingAmount_returns400() throws Exception {
            mockMvc.perform(post("/api/funds/1/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "sourceWalletId": 1,
                                      "date": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void missingDate_returns400() throws Exception {
            mockMvc.perform(post("/api/funds/1/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "amount": 500.00,
                                      "sourceWalletId": 1
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void depositToArchivedFund_returns409() throws Exception {
            when(fundService.depositToFund(eq(1L), any(), eq(USER_ID)))
                    .thenThrow(new ConflictException("Only active funds can accept deposits."));

            mockMvc.perform(post("/api/funds/1/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "amount": 500.00,
                                      "sourceWalletId": 1,
                                      "date": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isConflict());
        }

        @Test
        void wrongUserFund_returns404() throws Exception {
            when(fundService.depositToFund(eq(99L), any(), eq(USER_ID)))
                    .thenThrow(new EntityNotFoundException("Fund not found with id: 99"));

            mockMvc.perform(post("/api/funds/99/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "amount": 500.00,
                                      "sourceWalletId": 1,
                                      "date": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        void sourceWalletIsFundWallet_returns400() throws Exception {
            when(fundService.depositToFund(eq(1L), any(), eq(USER_ID)))
                    .thenThrow(new IllegalArgumentException("Source wallet cannot be a fund wallet."));

            mockMvc.perform(post("/api/funds/1/deposit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "amount": 500.00,
                                      "sourceWalletId": 100,
                                      "date": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Withdraw {

        @Test
        void returns200WithUpdatedFund() throws Exception {
            FundResponse updated = new FundResponse(
                    1L, "Vacation", null, new BigDecimal("5000.00"),
                    new BigDecimal("1500.00"), new BigDecimal("30.00"), new BigDecimal("3500.00"),
                    false, "PLN", FundStatus.ACTIVE, null, null,
                    null, null,
                    Instant.parse("2026-06-01T10:00:00Z"),
                    Instant.parse("2026-06-01T10:00:00Z")
            );
            when(fundService.withdrawFromFund(eq(1L), any(), eq(USER_ID))).thenReturn(updated);

            mockMvc.perform(post("/api/funds/1/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "amount": 500.00,
                                      "destinationWalletId": 1,
                                      "date": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentAmount").value(1500.00))
                    .andExpect(jsonPath("$.remainingAmount").value(3500.00));
        }

        @Test
        void missingAmount_returns400() throws Exception {
            mockMvc.perform(post("/api/funds/1/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "destinationWalletId": 1,
                                      "date": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void missingDate_returns400() throws Exception {
            mockMvc.perform(post("/api/funds/1/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "amount": 500.00,
                                      "destinationWalletId": 1
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void exceedsFundBalance_returns400() throws Exception {
            when(fundService.withdrawFromFund(eq(1L), any(), eq(USER_ID)))
                    .thenThrow(new IllegalArgumentException("Withdrawal amount 9000.00 exceeds fund balance 300.00."));

            mockMvc.perform(post("/api/funds/1/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "amount": 9000.00,
                                      "destinationWalletId": 1,
                                      "date": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void wrongUserFund_returns404() throws Exception {
            when(fundService.withdrawFromFund(eq(99L), any(), eq(USER_ID)))
                    .thenThrow(new EntityNotFoundException("Fund not found with id: 99"));

            mockMvc.perform(post("/api/funds/99/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "amount": 100.00,
                                      "destinationWalletId": 1,
                                      "date": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        void destinationWalletIsFundWallet_returns400() throws Exception {
            when(fundService.withdrawFromFund(eq(1L), any(), eq(USER_ID)))
                    .thenThrow(new IllegalArgumentException("Destination wallet cannot be a fund wallet."));

            mockMvc.perform(post("/api/funds/1/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "amount": 100.00,
                                      "destinationWalletId": 100,
                                      "date": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }
}
