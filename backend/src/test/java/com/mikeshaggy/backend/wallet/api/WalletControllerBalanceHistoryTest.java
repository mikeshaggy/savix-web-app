package com.mikeshaggy.backend.wallet.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryChartPointResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistorySummaryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelineEntryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelineGroupResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelinePaginationResponse;
import com.mikeshaggy.backend.ledger.service.WalletBalanceHistoryQueryService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
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

@WebMvcTest(WalletController.class)
@AutoConfigureMockMvc(addFilters = false)
class WalletControllerBalanceHistoryTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private WalletBalanceHistoryQueryService walletBalanceHistoryQueryService;

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
    void getBalanceHistoryByWalletId_returnsPreparedPayload() throws Exception {
        // given
        WalletBalanceHistoryResponse response =
                new WalletBalanceHistoryResponse(
                        1,
                        "Main",
                        new BigDecimal("974.50"),
                        new WalletBalanceHistorySummaryResponse(
                                new BigDecimal("974.50"),
                                new BigDecimal("1000.00"),
                                new BigDecimal("950.00"),
                                2,
                                new BigDecimal("-25.50")),
                        List.of(
                                new WalletBalanceHistoryChartPointResponse(
                                        LocalDate.of(2026, 3, 10), new BigDecimal("974.50"), new BigDecimal("-25.50"))),
                        List.of(
                                new WalletBalanceHistoryTimelineGroupResponse(
                                        LocalDate.of(2026, 3, 10),
                                        List.of(
                                                new WalletBalanceHistoryTimelineEntryResponse(
                                                        101L,
                                                        LocalDate.of(2026, 3, 10),
                                                        new BigDecimal("-25.50"),
                                                        new BigDecimal("974.50"),
                                                        com.mikeshaggy.backend.ledger.domain.SourceType.TRANSACTION,
                                                        55L,
                                                        "Transaction",
                                                        "#55")))),
                        new WalletBalanceHistoryTimelinePaginationResponse(0, 10, 1, 1, false, false));

        when(walletBalanceHistoryQueryService.getBalanceHistoryByWalletIdForUser(
                        eq(1),
                        eq(TEST_USER_ID),
                        eq(LocalDate.of(2026, 3, 1)),
                        eq(LocalDate.of(2026, 3, 31)),
                        eq(0),
                        eq(10)))
                .thenReturn(response);

        // when
        mockMvc
                .perform(
                        get("/api/wallets/1/balance-history")
                                .queryParam("from", "2026-03-01")
                                .queryParam("to", "2026-03-31")
                                .queryParam("page", "0")
                                .queryParam("size", "10"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(1))
                .andExpect(jsonPath("$.walletName").value("Main"))
                .andExpect(jsonPath("$.currentBalance").value(974.50))
                .andExpect(jsonPath("$.summary.latestBalance").value(974.50))
                .andExpect(jsonPath("$.chart[0].date").value("2026-03-10"))
                .andExpect(jsonPath("$.chart[0].closingBalance").value(974.50))
                .andExpect(jsonPath("$.timeline[0].date").value("2026-03-10"))
                .andExpect(jsonPath("$.timelinePagination.currentPage").value(0));
    }

    @Test
    void getBalanceHistoryByWalletId_walletNotOwnedByUser_returns404() throws Exception {
        // given
        when(walletBalanceHistoryQueryService.getBalanceHistoryByWalletIdForUser(
                        eq(77), eq(TEST_USER_ID), eq(null), eq(null), eq(0), eq(10)))
                .thenThrow(new EntityNotFoundException("Wallet not found with id: 77"));

        // when
        mockMvc
                .perform(get("/api/wallets/77/balance-history"))
                // then
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Wallet not found with id: 77"));
    }

    @Test
    void getBalanceHistoryByWalletId_invalidDateRange_returns400() throws Exception {
        // given
        when(walletBalanceHistoryQueryService.getBalanceHistoryByWalletIdForUser(
                        eq(1),
                        eq(TEST_USER_ID),
                        eq(LocalDate.of(2026, 3, 20)),
                        eq(LocalDate.of(2026, 3, 10)),
                        eq(0),
                        eq(10)))
                .thenThrow(
                        new IllegalArgumentException("from (2026-03-20) must not be after to (2026-03-10)"));

        // when
        mockMvc
                .perform(
                        get("/api/wallets/1/balance-history")
                                .queryParam("from", "2026-03-20")
                                .queryParam("to", "2026-03-10"))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message").value("from (2026-03-20) must not be after to (2026-03-10)"));
    }
}
