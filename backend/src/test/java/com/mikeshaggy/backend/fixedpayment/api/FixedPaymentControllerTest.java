package com.mikeshaggy.backend.fixedpayment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mikeshaggy.backend.auth.service.JwtService;
import com.mikeshaggy.backend.auth.util.cookie.AuthCookieManager;
import com.mikeshaggy.backend.common.exception.ConflictException;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedProgressDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.RiskIndicatorDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedPaymentResponse;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentCrudService;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentOccurrenceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private FixedPaymentOccurrenceService fixedPaymentOccurrenceService;

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

    @Nested
    class TileRows {

        @Test
        void tileRowsCarryBucketLateDaysAndVerdict() throws Exception {
            var paidRow = new FixedOccurrenceRowDto(
                    85L, 1, "Rent", 1, "Housing", "🏠", 1,
                    new BigDecimal("1900.00"), new BigDecimal("1879.91"),
                    LocalDate.of(2026, 9, 10), OccurrenceStatus.PAID, -4L,
                    LocalDate.of(2026, 9, 11).atStartOfDay(), 501L,
                    LocalDate.of(2026, 9, 11), 1, Boolean.FALSE,
                    FixedOccurrenceBucket.PAID_THIS_CYCLE);
            var upcomingRow = new FixedOccurrenceRowDto(
                    90L, 2, "Instalment", 2, "Loans", "💳", 1,
                    new BigDecimal("408.30"), null,
                    LocalDate.of(2026, 10, 8), OccurrenceStatus.PENDING, 24L,
                    null, null, null, null, null,
                    FixedOccurrenceBucket.LATER_THIS_CYCLE);
            var afterPaydayRow = new FixedOccurrenceRowDto(
                    120L, 2, "Instalment", 2, "Loans", "💳", 1,
                    new BigDecimal("408.30"), null,
                    LocalDate.of(2026, 11, 8), OccurrenceStatus.PENDING, 55L,
                    null, null, null, null, null,
                    FixedOccurrenceBucket.AFTER_PAYDAY);
            var summary = new FixedSummaryDto(
                    new BigDecimal("2308.30"), 2, new BigDecimal("1879.91"), 1,
                    new BigDecimal("408.30"), 1, BigDecimal.ZERO, 0,
                    new BigDecimal("24.44"), new BigDecimal("1900.00"));
            var tile = new FixedTransactionsTileDto(
                    LocalDate.of(2026, 9, 9), LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9),
                    LocalDate.of(2026, 10, 9), CycleState.OPEN,
                    summary, new FixedProgressDto(1, 2, new BigDecimal("50.00"), LocalDate.of(2026, 10, 8),
                            "Instalment", "Instalment", new BigDecimal("408.30"), 2),
                    new BigDecimal("5896.89"), new BigDecimal("5488.59"), new RiskIndicatorDto(false, null),
                    List.of(), List.of(upcomingRow), List.of(paidRow), LocalDate.of(2026, 11, 9), List.of(afterPaydayRow));
            when(fixedPaymentDashboardService.getFixedPaymentsTileDataForCurrentPeriod(1, TEST_USER_ID))
                    .thenReturn(tile);

            mockMvc
                    .perform(get("/api/fixed-payments/tile").param("walletId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodEnd").value("2026-10-08"))
                    .andExpect(jsonPath("$.expectedPaydayDate").value("2026-10-09"))
                    .andExpect(jsonPath("$.upcoming[0].occurrenceId").value(90))
                    .andExpect(jsonPath("$.upcoming[0].bucket").value("LATER_THIS_CYCLE"))
                    .andExpect(jsonPath("$.upcoming[0].lateDays").doesNotExist())
                    .andExpect(jsonPath("$.paid[0].occurrenceId").value(85))
                    .andExpect(jsonPath("$.paid[0].bucket").value("PAID_THIS_CYCLE"))
                    .andExpect(jsonPath("$.paid[0].paidDate").value("2026-09-11"))
                    .andExpect(jsonPath("$.paid[0].lateDays").value(1))
                    .andExpect(jsonPath("$.paid[0].paidOnTime").value(false))
                    .andExpect(jsonPath("$.afterPaydayEnd").value("2026-11-09"))
                    .andExpect(jsonPath("$.afterPayday[0].occurrenceId").value(120))
                    .andExpect(jsonPath("$.afterPayday[0].bucket").value("AFTER_PAYDAY"))
                    .andExpect(jsonPath("$.summary.paidAmount").value(1879.91))
                    .andExpect(jsonPath("$.summary.plannedPaidAmount").value(1900.00));
        }
    }

    @Nested
    class LinkUnlinkOccurrence {

        private FixedOccurrenceRowDto occurrenceDto(OccurrenceStatus status, Long transactionId) {
            return new FixedOccurrenceRowDto(
                    10L, 1, "Rent", 1, "Housing", "🏠", 1,
                    new BigDecimal("1500.00"),
                    transactionId == null ? null : new BigDecimal("1480.00"),
                    LocalDate.of(2026, 3, 1), status, 5L,
                    transactionId == null ? null : LocalDate.of(2026, 3, 2).atStartOfDay(),
                    transactionId,
                    transactionId == null ? null : LocalDate.of(2026, 3, 2),
                    transactionId == null ? null : 1,
                    transactionId == null ? null : Boolean.FALSE,
                    null);
        }

        @Test
        void link_returns200WithLinkedOccurrence() throws Exception {
            when(fixedPaymentOccurrenceService.linkExistingTransaction(10L, 123L, TEST_USER_ID))
                    .thenReturn(occurrenceDto(OccurrenceStatus.PAID, 123L));

            mockMvc
                    .perform(
                            post("/api/fixed-payments/occurrences/10/link")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"transactionId": 123}
                                            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.occurrenceId").value(10))
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.transactionId").value(123))
                    .andExpect(jsonPath("$.paidDate").value("2026-03-02"))
                    .andExpect(jsonPath("$.lateDays").value(1))
                    .andExpect(jsonPath("$.paidOnTime").value(false))
                    // no cycle context on the link response: the page re-fetches the tile for the bucket
                    .andExpect(jsonPath("$.bucket").doesNotExist());
        }

        @Test
        void link_missingTransactionId_returns400() throws Exception {
            mockMvc
                    .perform(
                            post("/api/fixed-payments/occurrences/10/link")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.transactionId").exists());
        }

        @Test
        void link_alreadyLinked_returns409() throws Exception {
            when(fixedPaymentOccurrenceService.linkExistingTransaction(10L, 123L, TEST_USER_ID))
                    .thenThrow(new ConflictException("Occurrence is already linked to a transaction"));

            mockMvc
                    .perform(
                            post("/api/fixed-payments/occurrences/10/link")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"transactionId": 123}
                                            """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void unlink_returns200WithResetOccurrence() throws Exception {
            when(fixedPaymentOccurrenceService.unlinkByOccurrenceId(10L, TEST_USER_ID))
                    .thenReturn(occurrenceDto(OccurrenceStatus.PENDING, null));

            mockMvc
                    .perform(delete("/api/fixed-payments/occurrences/10/link"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.occurrenceId").value(10))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.transactionId").doesNotExist())
                    .andExpect(jsonPath("$.paidDate").doesNotExist())
                    .andExpect(jsonPath("$.lateDays").doesNotExist())
                    .andExpect(jsonPath("$.paidOnTime").doesNotExist());
        }
    }
}
