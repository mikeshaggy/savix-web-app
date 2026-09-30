package com.mikeshaggy.backend.ledger.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.ledger.domain.SourceType;
import com.mikeshaggy.backend.ledger.dto.BalanceSparklineResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryChartPointResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistorySummaryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelineEntryResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelineGroupResponse;
import com.mikeshaggy.backend.ledger.dto.WalletBalanceHistoryTimelinePaginationResponse;
import com.mikeshaggy.backend.ledger.dto.WalletEntryResponse;
import com.mikeshaggy.backend.ledger.service.BalanceSparklineService;
import com.mikeshaggy.backend.ledger.service.WalletEntryService;
import java.math.BigDecimal;
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

@WebMvcTest(WalletEntryController.class)
@AutoConfigureMockMvc(addFilters = false)
class WalletEntryControllerTest {

    private static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletEntryService walletEntryService;

    @MockitoBean
    private BalanceSparklineService balanceSparklineService;

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
    void getEntriesByWalletId_returnsBalanceAfterField() throws Exception {
        // given
        WalletEntryResponse response =
                new WalletEntryResponse(
                        101L,
                        1,
                        "Main",
                        new BigDecimal("-25.50"),
                        new BigDecimal("974.50"),
                        LocalDate.of(2026, 3, 10),
                        SourceType.TRANSACTION,
                        55L,
                        Instant.parse("2026-03-10T08:00:00Z"));

        when(walletEntryService.getEntriesByWalletIdForUser(
                        eq(1), eq(TEST_USER_ID), isNull(), isNull(), isNull()))
                .thenReturn(List.of(response));

        // when
        mockMvc
                .perform(get("/api/wallet-entries/wallet/1"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].amountSigned").value(-25.50))
                .andExpect(jsonPath("$[0].balanceAfter").value(974.50))
                .andExpect(jsonPath("$[0].sourceType").value("TRANSACTION"));
    }

    @Test
    void getBalanceHistoryByWalletId_returnsPreparedBalanceHistoryPayload() throws Exception {
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
                                                        SourceType.TRANSACTION,
                                                        55L,
                                                        "Transaction",
                                                        "#55")))),
                        new WalletBalanceHistoryTimelinePaginationResponse(0, 10, 1, 1, false, false));

        when(walletEntryService.getBalanceHistoryByWalletIdForUser(1, TEST_USER_ID, null, null, 0, 10))
                .thenReturn(response);

        // when
        mockMvc
                .perform(get("/api/wallet-entries/wallet/1/balance-history"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(1))
                .andExpect(jsonPath("$.summary.latestBalance").value(974.50))
                .andExpect(jsonPath("$.chart[0].date").value("2026-03-10"))
                .andExpect(jsonPath("$.chart[0].closingBalance").value(974.50))
                .andExpect(jsonPath("$.timeline[0].date").value("2026-03-10"))
                .andExpect(jsonPath("$.timeline[0].entries[0].sourceLabel").value("Transaction"))
                .andExpect(jsonPath("$.timeline[0].entries[0].sourceReference").value("#55"))
                .andExpect(jsonPath("$.timelinePagination.currentPage").value(0));
    }

    @Test
    void getBalanceSparkline_defaultsToNinetyDaysAndSerializesContract() throws Exception {
        // given
        BalanceSparklineResponse response =
                new BalanceSparklineResponse(
                        1,
                        LocalDate.of(2026, 6, 17),
                        LocalDate.of(2026, 9, 14),
                        List.of(new BalanceSparklineResponse.Point(LocalDate.of(2026, 9, 14), new BigDecimal("5896.89"))),
                        List.of(LocalDate.of(2026, 9, 9)),
                        List.of(new BalanceSparklineResponse.CycleMinimum(
                                LocalDate.of(2026, 8, 10),
                                LocalDate.of(2026, 9, 8),
                                new BigDecimal("-93.29"),
                                LocalDate.of(2026, 9, 5))));
        when(balanceSparklineService.getSparkline(1, TEST_USER_ID, 90)).thenReturn(response);

        // when / then
        mockMvc.perform(get("/api/wallet-entries/wallet/1/balance-history/sparkline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(1))
                .andExpect(jsonPath("$.from").value("2026-06-17"))
                .andExpect(jsonPath("$.to").value("2026-09-14"))
                .andExpect(jsonPath("$.points[0].date").value("2026-09-14"))
                .andExpect(jsonPath("$.points[0].balance").value(5896.89))
                .andExpect(jsonPath("$.paydays[0]").value("2026-09-09"))
                .andExpect(jsonPath("$.cycleMinima[0].cycleStart").value("2026-08-10"))
                .andExpect(jsonPath("$.cycleMinima[0].cycleEnd").value("2026-09-08"))
                .andExpect(jsonPath("$.cycleMinima[0].minBalance").value(-93.29))
                .andExpect(jsonPath("$.cycleMinima[0].date").value("2026-09-05"));
    }

    @Test
    void getBalanceSparkline_passesDaysAndMapsInvalidDaysToBadRequest() throws Exception {
        // given
        when(balanceSparklineService.getSparkline(1, TEST_USER_ID, 400))
                .thenThrow(new IllegalArgumentException("days must be between 1 and 366"));

        // when / then
        mockMvc.perform(get("/api/wallet-entries/wallet/1/balance-history/sparkline").param("days", "400"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("days must be between 1 and 366"));
    }

    @Test
    void getBalanceSparkline_foreignWalletIsNotFound() throws Exception {
        // given
        when(balanceSparklineService.getSparkline(99, TEST_USER_ID, 90))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("Wallet not found with id: 99"));

        // when / then
        mockMvc.perform(get("/api/wallet-entries/wallet/99/balance-history/sparkline"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBalanceSparkline_nonNumericDaysIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/wallet-entries/wallet/1/balance-history/sparkline").param("days", "abc"))
                .andExpect(status().isBadRequest());
    }
}
