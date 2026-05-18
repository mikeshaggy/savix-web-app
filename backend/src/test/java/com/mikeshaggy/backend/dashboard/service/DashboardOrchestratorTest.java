package com.mikeshaggy.backend.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.mikeshaggy.backend.dashboard.dto.*;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentOccurrenceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardOrchestratorTest {

    @Mock
    private FixedPaymentOccurrenceService fixedPaymentOccurrenceService;

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardOrchestrator dashboardOrchestrator;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Integer WALLET_ID = 1;

    private DashboardData dashboardData(PeriodType periodType) {
        PeriodDto period =
                new PeriodDto(
                        LocalDate.of(2026, 2, 25),
                        LocalDate.of(2026, 3, 11),
                        LocalDate.of(2026, 3, 25),
                        periodType);
        SummaryDto summary =
                new SummaryDto(
                        new BigDecimal("3500.00"),
                        new BigDecimal("2000.00"),
                        new BigDecimal("1500.00"),
                        new BigDecimal("42.86"),
                        new PercentageChangeDto(BigDecimal.ZERO, true),
                        new PercentageChangeDto(BigDecimal.ZERO, true),
                        new PercentageChangeDto(BigDecimal.ZERO, true));
        return new DashboardData(
                period,
                summary,
                Collections.emptyList(),
                "Main Wallet",
                mock(FixedTransactionsTileDto.class));
    }

    @Nested
    class WriteThenReadContract {

        @Test
        void preparesOccurrencesBeforeFetchingDashboardData() {
            // given
            DashboardData expected = dashboardData(PeriodType.PAY_CYCLE);
            when(dashboardService.getDashboardData(USER_ID, WALLET_ID, null, null, PeriodType.PAY_CYCLE))
                    .thenReturn(expected);

            // when
            DashboardData result =
                    dashboardOrchestrator.getDashboardData(
                            USER_ID, WALLET_ID, null, null, PeriodType.PAY_CYCLE);

            InOrder inOrder = inOrder(fixedPaymentOccurrenceService, dashboardService);
            inOrder.verify(fixedPaymentOccurrenceService).prepareOccurrencesForDashboard(USER_ID);
            inOrder
                    .verify(dashboardService)
                    .getDashboardData(USER_ID, WALLET_ID, null, null, PeriodType.PAY_CYCLE);

            // then
            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    class ParameterPassThrough {

        @Test
        void customPeriodParametersPassedToDashboardService() {
            // given
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 31);
            DashboardData expected = dashboardData(PeriodType.CUSTOM);
            when(dashboardService.getDashboardData(
                            USER_ID, WALLET_ID, startDate, endDate, PeriodType.CUSTOM))
                    .thenReturn(expected);

            // when
            DashboardData result =
                    dashboardOrchestrator.getDashboardData(
                            USER_ID, WALLET_ID, startDate, endDate, PeriodType.CUSTOM);

            // then
            assertThat(result).isSameAs(expected);
            verify(dashboardService)
                    .getDashboardData(USER_ID, WALLET_ID, startDate, endDate, PeriodType.CUSTOM);
        }

        @Test
        void walletIdPassedThroughCorrectly() {
            // given
            Integer specificWalletId = 99;
            DashboardData expected = dashboardData(PeriodType.PAY_CYCLE);
            when(dashboardService.getDashboardData(
                            USER_ID, specificWalletId, null, null, PeriodType.PAY_CYCLE))
                    .thenReturn(expected);

            // when
            DashboardData result =
                    dashboardOrchestrator.getDashboardData(
                            USER_ID, specificWalletId, null, null, PeriodType.PAY_CYCLE);

            // then
            assertThat(result).isSameAs(expected);
            verify(fixedPaymentOccurrenceService).prepareOccurrencesForDashboard(USER_ID);
            verify(dashboardService)
                    .getDashboardData(USER_ID, specificWalletId, null, null, PeriodType.PAY_CYCLE);
        }
    }

    @Nested
    class ErrorPropagation {

        @Test
        void exceptionInPreparePropagatesToCaller() {
            // given
            doThrow(new RuntimeException("DB failure"))
                    .when(fixedPaymentOccurrenceService)
                    .prepareOccurrencesForDashboard(USER_ID);

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    dashboardOrchestrator.getDashboardData(
                                            USER_ID, WALLET_ID, null, null, PeriodType.PAY_CYCLE))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB failure");

            verifyNoInteractions(dashboardService);
        }

        @Test
        void exceptionInDashboardServicePropagatesToCaller() {
            // given
            when(dashboardService.getDashboardData(USER_ID, WALLET_ID, null, null, PeriodType.PAY_CYCLE))
                    .thenThrow(new IllegalArgumentException("Unsupported period type"));

            // when
            // then
            assertThatThrownBy(
                            () ->
                                    dashboardOrchestrator.getDashboardData(
                                            USER_ID, WALLET_ID, null, null, PeriodType.PAY_CYCLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported period type");
        }
    }
}
