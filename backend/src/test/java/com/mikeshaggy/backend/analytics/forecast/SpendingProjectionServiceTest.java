package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.fixedpayment.dto.FixedProgressDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.RiskIndicatorDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpendingProjectionServiceTest {

    private static final Integer WALLET_ID = 1;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 19);

    @Mock
    private AnalyticsTransactionQueryService transactionQueryService;

    @Mock
    private WalletService walletService;

    @Mock
    private PeriodService periodService;

    @Mock
    private FixedPaymentDashboardService fixedPaymentDashboardService;

    private SpendingProjectionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-19T10:00:00Z"), ZoneOffset.UTC);
        service = new SpendingProjectionService(
                transactionQueryService, walletService, periodService,
                fixedPaymentDashboardService, new SpendingProjectionCalculator(), clock);
        Wallet wallet = Wallet.builder().id(WALLET_ID).balance(new BigDecimal("1000.00")).build();
        lenient().when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID)).thenReturn(wallet);
        lenient().when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                        any(), any(Wallet.class), eq(USER_ID), any(LocalDate.class)))
                .thenReturn(fixedTile(new BigDecimal("0.00")));
    }

    @Test
    void defaultPayCycleCurrentPeriod_projectsThroughBillingEndDate() {
        when(periodService.resolve(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 10), TODAY,
                        LocalDate.of(2026, 6, 10), PeriodType.PAY_CYCLE));
        sums(new BigDecimal("5000.00"), new BigDecimal("5000.00"), new BigDecimal("1850.00"));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                        any(), any(Wallet.class), eq(USER_ID), eq(TODAY)))
                .thenReturn(fixedTile(new BigDecimal("350.00")));

        SpendingProjectionDto result = service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null);

        assertThat(result.periodType()).isEqualTo(PeriodType.PAY_CYCLE);
        assertThat(result.periodLabel()).isEqualTo("Current pay cycle");
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(result.daysInPeriod()).isEqualTo(31);
        assertThat(result.daysElapsed()).isEqualTo(10);
        assertThat(result.daysRemaining()).isEqualTo(21);
        assertThat(result.dailyBurnRate()).isEqualByComparingTo("185.00");
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("5735.00");
        assertThat(result.projectedEndBalance()).isEqualByComparingTo("-735.00");
        assertThat(result.remainingFixedPayments()).isEqualByComparingTo("350.00");
        assertThat(result.safeToSpendToday()).isEqualByComparingTo("-3235.00");
        // -3235.00 / 21 = -154.05 (HALF_UP)
        assertThat(result.safeToSpendPerDay()).isEqualByComparingTo("-154.05");
        assertThat(result.projectionAvailable()).isTrue();
        assertThat(result.projectionReason()).isNull();
    }

    @Test
    void explicitPayCycleCurrentPeriod_usesSameContract() {
        when(periodService.resolve(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 1), TODAY,
                        LocalDate.of(2026, 6, 1), PeriodType.PAY_CYCLE));
        sums(new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("190.00"));

        SpendingProjectionDto result = service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null);

        assertThat(result.daysElapsed()).isEqualTo(19);
        assertThat(result.dailyBurnRate()).isEqualByComparingTo("10.00");
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("310.00");
    }

    @Test
    void lastPayCycleHistoricalPeriod_returnsActualsOnly() {
        when(periodService.resolve(PeriodType.LAST_PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 9),
                        LocalDate.of(2026, 5, 10), PeriodType.LAST_PAY_CYCLE));
        sums(new BigDecimal("4000.00"), new BigDecimal("4000.00"), new BigDecimal("2500.00"));

        SpendingProjectionDto result = service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.LAST_PAY_CYCLE, null, null);

        assertThat(result.projectionAvailable()).isFalse();
        assertThat(result.projectionReason()).isEqualTo("Historical period");
        assertThat(result.daysElapsed()).isEqualTo(30);
        assertThat(result.daysRemaining()).isZero();
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("2500.00");
        assertThat(result.projectedEndBalance()).isEqualByComparingTo("1500.00");
        assertThat(result.remainingFixedPayments()).isEqualByComparingTo("0.00");
        assertThat(result.safeToSpendToday()).isEqualByComparingTo("0.00");
        verify(fixedPaymentDashboardService, never()).getFixedPaymentsTileData(
                any(), any(Wallet.class), any(), any(LocalDate.class));
    }

    @Test
    void lastPayCycleUsesResolvedEndDateNotBillingEndDate() {
        when(periodService.resolve(PeriodType.LAST_PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 14),
                        LocalDate.of(2026, 5, 10), PeriodType.LAST_PAY_CYCLE));
        sums(new BigDecimal("4000.00"), new BigDecimal("4000.00"), new BigDecimal("2500.00"));

        SpendingProjectionDto result = service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.LAST_PAY_CYCLE, null, null);

        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 5, 14));
        assertThat(result.daysInPeriod()).isEqualTo(35);
        assertThat(result.daysElapsed()).isEqualTo(35);
    }

    @Test
    void monthlyCurrentMonth_usesCalendarMonth() {
        when(periodService.resolve(PeriodType.MONTHLY, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 6, 1), PeriodType.MONTHLY));
        sums(new BigDecimal("3100.00"), new BigDecimal("3100.00"), new BigDecimal("950.00"));

        SpendingProjectionDto result = service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null);

        assertThat(result.periodLabel()).isEqualTo("Current month");
        assertThat(result.daysInPeriod()).isEqualTo(31);
        assertThat(result.daysElapsed()).isEqualTo(19);
        assertThat(result.daysRemaining()).isEqualTo(12);
    }

    @Test
    void customPeriodContainingToday_projectsSelectedRange() {
        LocalDate start = LocalDate.of(2026, 5, 15);
        LocalDate end = LocalDate.of(2026, 5, 25);
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, start, end))
                .thenReturn(new PeriodDto(start, end, start.plusMonths(1), PeriodType.CUSTOM));
        sums(new BigDecimal("700.00"), new BigDecimal("900.00"), new BigDecimal("250.00"));

        SpendingProjectionDto result = service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.CUSTOM, start, end);

        assertThat(result.periodLabel()).isEqualTo("Custom range");
        assertThat(result.daysInPeriod()).isEqualTo(11);
        assertThat(result.daysElapsed()).isEqualTo(5);
        assertThat(result.daysRemaining()).isEqualTo(6);
        assertThat(result.dailyBurnRate()).isEqualByComparingTo("50.00");
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("550.00");
        assertThat(result.projectedEndBalance()).isEqualByComparingTo("350.00");
    }

    @Test
    void customHistoricalPeriod_marksProjectionUnavailable() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, start, end))
                .thenReturn(new PeriodDto(start, end, start.plusMonths(1), PeriodType.CUSTOM));
        sums(new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("800.00"));

        SpendingProjectionDto result = service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.CUSTOM, start, end);

        assertThat(result.projectionAvailable()).isFalse();
        assertThat(result.daysElapsed()).isEqualTo(31);
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("800.00");
    }

    @Test
    void customFutureOnlyPeriodRejected() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, start, end))
                .thenReturn(new PeriodDto(start, end, start.plusMonths(1), PeriodType.CUSTOM));

        assertThatThrownBy(() -> service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.CUSTOM, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("period must not be in the future");
    }

    @Test
    void customMissingStartOrEndRejectedByPeriodService() {
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, null, LocalDate.of(2026, 5, 31)))
                .thenThrow(new IllegalArgumentException("Both startDate and endDate are required for CUSTOM period type"));

        assertThatThrownBy(() -> service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.CUSTOM, null, LocalDate.of(2026, 5, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Both startDate and endDate are required for CUSTOM period type");
    }

    @Test
    void customStartAfterEndRejectedByPeriodService() {
        LocalDate start = LocalDate.of(2026, 5, 31);
        LocalDate end = LocalDate.of(2026, 5, 1);
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, start, end))
                .thenThrow(new IllegalArgumentException("startDate (2026-05-31) must not be after endDate (2026-05-01)"));

        assertThatThrownBy(() -> service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.CUSTOM, start, end))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startDate (2026-05-31) must not be after endDate (2026-05-01)");
    }

    @Test
    void firstDayOfPeriod_countsOneElapsedDay() {
        service = serviceWithDate(LocalDate.of(2026, 5, 10));
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20)))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20),
                        LocalDate.of(2026, 6, 10), PeriodType.CUSTOM));
        sums(new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("25.00"));

        SpendingProjectionDto result = service.getSpendingProjection(WALLET_ID, USER_ID, PeriodType.CUSTOM,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 20));

        assertThat(result.daysElapsed()).isEqualTo(1);
        assertThat(result.daysRemaining()).isEqualTo(10);
        assertThat(result.dailyBurnRate()).isEqualByComparingTo("25.00");
    }

    @Test
    void middleOfPeriod_countsInclusiveElapsedDays() {
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 30)))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 30),
                        LocalDate.of(2026, 6, 10), PeriodType.CUSTOM));
        sums(new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"));

        SpendingProjectionDto result = service.getSpendingProjection(WALLET_ID, USER_ID, PeriodType.CUSTOM,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 30));

        assertThat(result.daysElapsed()).isEqualTo(10);
        assertThat(result.daysRemaining()).isEqualTo(11);
    }

    @Test
    void lastDayOfPeriod_hasNoRemainingDays() {
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID,
                LocalDate.of(2026, 5, 10), TODAY))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 10), TODAY,
                        LocalDate.of(2026, 6, 10), PeriodType.CUSTOM));
        sums(new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"));

        SpendingProjectionDto result = service.getSpendingProjection(WALLET_ID, USER_ID, PeriodType.CUSTOM,
                LocalDate.of(2026, 5, 10), TODAY);

        assertThat(result.daysElapsed()).isEqualTo(10);
        assertThat(result.daysRemaining()).isZero();
    }

    @Test
    void zeroExpensesReturnsZeroBurnRateAndProjectedExpenses() {
        when(periodService.resolve(PeriodType.MONTHLY, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 6, 1), PeriodType.MONTHLY));
        sums(new BigDecimal("1000.00"), new BigDecimal("1000.00"), BigDecimal.ZERO);

        SpendingProjectionDto result = service.getSpendingProjection(WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null);

        assertThat(result.dailyBurnRate()).isEqualByComparingTo("0.00");
        assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("0.00");
    }

    @Test
    void zeroIncomeDoesNotCrash() {
        when(periodService.resolve(PeriodType.MONTHLY, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 6, 1), PeriodType.MONTHLY));
        sums(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("190.00"));

        SpendingProjectionDto result = service.getSpendingProjection(WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null);

        assertThat(result.incomeForPeriod()).isEqualByComparingTo("0.00");
        assertThat(result.projectedEndBalance()).isEqualByComparingTo("-310.00");
    }

    @Test
    void zeroIncomeAndZeroExpensesReturnsZeros() {
        when(periodService.resolve(PeriodType.MONTHLY, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 6, 1), PeriodType.MONTHLY));
        sums(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        SpendingProjectionDto result = service.getSpendingProjection(WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null);

        assertThat(result.dailyBurnRate()).isEqualByComparingTo("0.00");
        assertThat(result.projectedEndBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void divisionByZeroGuardReturnsZeroBurnRate() {
        when(periodService.resolve(PeriodType.CUSTOM, WALLET_ID, USER_ID, TODAY, TODAY.minusDays(1)))
                .thenReturn(new PeriodDto(TODAY, TODAY.minusDays(1), TODAY, PeriodType.CUSTOM));
        sums(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"));

        SpendingProjectionDto result = service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.CUSTOM, TODAY, TODAY.minusDays(1));

        assertThat(result.daysInPeriod()).isZero();
        assertThat(result.daysElapsed()).isZero();
        assertThat(result.dailyBurnRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void negativeProjectedEndBalanceAllowed() {
        when(periodService.resolve(PeriodType.MONTHLY, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 6, 1), PeriodType.MONTHLY));
        sums(new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("1900.00"));

        SpendingProjectionDto result = service.getSpendingProjection(WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null);

        assertThat(result.projectedEndBalance()).isNegative();
    }

    @Test
    void negativeSafeToSpendTodayAllowed() {
        Wallet wallet = Wallet.builder().id(WALLET_ID).balance(new BigDecimal("100.00")).build();
        when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID)).thenReturn(wallet);
        when(periodService.resolve(PeriodType.MONTHLY, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 6, 1), PeriodType.MONTHLY));
        sums(new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1900.00"));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                        any(), any(Wallet.class), eq(USER_ID), eq(TODAY)))
                .thenReturn(fixedTile(new BigDecimal("200.00")));

        SpendingProjectionDto result = service.getSpendingProjection(WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null);

        assertThat(result.safeToSpendToday()).isNegative();
    }

    @Test
    void leapYearFebruaryMonthlyCountsTwentyNineDays() {
        service = serviceWithDate(LocalDate.of(2028, 2, 10));
        when(periodService.resolve(PeriodType.MONTHLY, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29),
                        LocalDate.of(2028, 3, 1), PeriodType.MONTHLY));
        sums(new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("100.00"));

        SpendingProjectionDto result = service.getSpendingProjection(WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null);

        assertThat(result.daysInPeriod()).isEqualTo(29);
        assertThat(result.daysElapsed()).isEqualTo(10);
        assertThat(result.daysRemaining()).isEqualTo(19);
    }

    @Test
    void remainingFixedPaymentsIncludedForCurrentPeriod() {
        when(periodService.resolve(PeriodType.MONTHLY, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 6, 1), PeriodType.MONTHLY));
        sums(new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("190.00"));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                        any(), any(Wallet.class), eq(USER_ID), eq(TODAY)))
                .thenReturn(fixedTile(new BigDecimal("350.00")));

        SpendingProjectionDto result = service.getSpendingProjection(WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null);

        assertThat(result.remainingFixedPayments()).isEqualByComparingTo("350.00");
        ArgumentCaptor<PeriodDto> captor = ArgumentCaptor.forClass(PeriodDto.class);
        verify(fixedPaymentDashboardService).getFixedPaymentsTileData(
                captor.capture(), any(Wallet.class), eq(USER_ID), eq(TODAY));
        assertThat(captor.getValue().billingEndDate()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void asOfDateIsPassedToRemainingFixedPaymentsCalculation() {
        LocalDate asOfDate = LocalDate.of(2026, 5, 10);
        when(periodService.resolve(PeriodType.MONTHLY, WALLET_ID, USER_ID, null, null))
                .thenReturn(new PeriodDto(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                        LocalDate.of(2026, 6, 1), PeriodType.MONTHLY));
        sums(new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("100.00"));
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(asOfDate)))
                .thenReturn(fixedTile(new BigDecimal("250.00")));

        SpendingProjectionDto result = service.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null, asOfDate);

        assertThat(result.daysElapsed()).isEqualTo(10);
        assertThat(result.remainingFixedPayments()).isEqualByComparingTo("250.00");
        verify(fixedPaymentDashboardService).getFixedPaymentsTileData(
                any(), any(Wallet.class), eq(USER_ID), eq(asOfDate));
    }

    private void sums(BigDecimal incomeToDate, BigDecimal incomeForPeriod, BigDecimal expensesToDate) {
        lenient().when(transactionQueryService.sum(
                        eq(WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class), eq(CategoryType.INCOME)))
                .thenReturn(incomeToDate, incomeForPeriod);
        lenient().when(transactionQueryService.sum(
                        eq(WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class), eq(CategoryType.EXPENSE)))
                .thenReturn(expensesToDate);
    }

    private SpendingProjectionService serviceWithDate(LocalDate date) {
        Clock clock = Clock.fixed(date.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new SpendingProjectionService(
                transactionQueryService, walletService, periodService,
                fixedPaymentDashboardService, new SpendingProjectionCalculator(), clock);
    }

    private FixedTransactionsTileDto fixedTile(BigDecimal remainingAmount) {
        FixedSummaryDto summary = new FixedSummaryDto(
                remainingAmount, 1,
                BigDecimal.ZERO, 0,
                remainingAmount, 1,
                BigDecimal.ZERO, 0,
                BigDecimal.ZERO);
        FixedProgressDto progress = new FixedProgressDto(
                0, 1, BigDecimal.ZERO,
                null, null, null, null, 1);
        return new FixedTransactionsTileDto(
                TODAY, TODAY, TODAY,
                summary,
                progress,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new RiskIndicatorDto(false, null),
                List.of(),
                List.of(),
                List.of());
    }
}
