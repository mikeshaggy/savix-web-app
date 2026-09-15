package com.mikeshaggy.backend.analytics.overview;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyExpenseStats;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Stage 2.2 (AC-5): {@code AnalyticsSummaryService} must be null-safe when the underlying
 * projection is unavailable (MONTHLY / CUSTOM / LAST_PAY_CYCLE / AWAITING_SALARY / a closed
 * or legacy-resolved PAY_CYCLE) — {@code status} and {@code savingsRate} come back {@code null}
 * instead of throwing on the now-nullable {@code BigDecimal} projection fields.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsSummaryServiceTest {

    private static final Integer WALLET_ID = 1;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate START = LocalDate.of(2026, 5, 1);
    private static final LocalDate END = LocalDate.of(2026, 5, 31);

    @Mock private SpendingProjectionService spendingProjectionService;
    @Mock private PeriodService periodService;
    @Mock private AnalyticsTransactionQueryService transactionQueryService;
    @Mock private WalletService walletService;
    @Mock private CategoryAggregationService categoryAggregationService;

    private AnalyticsSummaryService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsSummaryService(
                spendingProjectionService, periodService, transactionQueryService,
                walletService, categoryAggregationService, new OverviewStatusCalculator());
        lenient().when(walletService.getWalletEntityByIdForUser(WALLET_ID, USER_ID))
                .thenReturn(Wallet.builder().id(WALLET_ID).balance(new BigDecimal("1000.00")).build());
        lenient().when(categoryAggregationService.aggregateExpenses(
                        eq(WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class),
                        eq(CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)))
                .thenReturn(new CategoryAggregationResult(BigDecimal.ZERO, List.of()));
        lenient().when(transactionQueryService.dailyExpenseStats(
                        eq(WALLET_ID), eq(USER_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new DailyExpenseStats(null, null, 0));
    }

    @Test
    void reportingPeriodWithUnavailableProjectionYieldsNullStatusAndSavingsRateWithoutNpe() {
        // MONTHLY (and CUSTOM/LAST_PAY_CYCLE/AWAITING_SALARY) projections carry every
        // projected figure as null — the six fields listed in AC-3.
        SpendingProjectionDto reportingProjection = new SpendingProjectionDto(
                PeriodType.MONTHLY, "Current month", START, END,
                31, 20, 11,
                new BigDecimal("4000.00"), new BigDecimal("4000.00"), new BigDecimal("2200.00"),
                null, null, null,
                BigDecimal.ZERO,
                null, null,
                new BigDecimal("2200.00"), BigDecimal.ZERO, new BigDecimal("110.00"), null,
                false, "REPORTING_PERIOD");
        when(spendingProjectionService.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null))
                .thenReturn(reportingProjection);
        when(periodService.resolvePeriods(PeriodType.MONTHLY, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(
                        PeriodDto.of(START, END, END.plusDays(1), PeriodType.MONTHLY), null));

        assertThatCode(() -> service.getSummary(WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null))
                .doesNotThrowAnyException();

        AnalyticsSummaryDto result = service.getSummary(WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null);

        assertThat(result.status()).isNull();
        assertThat(result.savingsRate()).isNull();
        assertThat(result.projectionAvailable()).isFalse();
        assertThat(result.projectedEndBalance()).isNull();
        assertThat(result.safeToSpend()).isNull();
        assertThat(result.safeToSpendPerDay()).isNull();
        assertThat(result.dailyBurnRate()).isNull();
        assertThat(result.projectedTotalSpend()).isNull();
        // actuals survive even though the projection is unavailable
        assertThat(result.incomeForPeriod()).isEqualByComparingTo("4000.00");
        assertThat(result.periodStart()).isEqualTo(START);
        assertThat(result.periodEnd()).isEqualTo(END);
    }

    @Test
    void awaitingSalaryProjectionYieldsNullStatusAndSavingsRateWithoutNpe() {
        SpendingProjectionDto awaitingProjection = new SpendingProjectionDto(
                PeriodType.PAY_CYCLE, "Current pay cycle", START, END,
                31, 31, 0,
                new BigDecimal("3000.00"), new BigDecimal("3000.00"), new BigDecimal("2900.00"),
                null, null, null,
                BigDecimal.ZERO,
                null, null,
                new BigDecimal("2900.00"), BigDecimal.ZERO, new BigDecimal("93.55"), null,
                false, "AWAITING_SALARY");
        when(spendingProjectionService.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null))
                .thenReturn(awaitingProjection);
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(
                        new PeriodDto(START, END, END.plusDays(1), PeriodType.PAY_CYCLE,
                                CycleState.AWAITING_SALARY, END.plusDays(1), true),
                        null));

        AnalyticsSummaryDto result = service.getSummary(WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null);

        assertThat(result.status()).isNull();
        assertThat(result.savingsRate()).isNull();
        assertThat(result.projectionAvailable()).isFalse();
    }

    @Test
    void openPayCycleWithAvailableProjectionComputesStatusAndSavingsRate() {
        SpendingProjectionDto openProjection = new SpendingProjectionDto(
                PeriodType.PAY_CYCLE, "Current pay cycle", START, END,
                31, 20, 11,
                new BigDecimal("4000.00"), new BigDecimal("4000.00"), new BigDecimal("2200.00"),
                new BigDecimal("110.00"), new BigDecimal("3410.00"), new BigDecimal("590.00"),
                BigDecimal.ZERO,
                new BigDecimal("590.00"), new BigDecimal("53.64"),
                new BigDecimal("2200.00"), BigDecimal.ZERO, new BigDecimal("110.00"), new BigDecimal("1210.00"),
                true, null);
        when(spendingProjectionService.getSpendingProjection(
                WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null))
                .thenReturn(openProjection);
        when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, WALLET_ID, USER_ID, null, null))
                .thenReturn(new ResolvedPeriods(
                        new PeriodDto(START, END, END.plusDays(1), PeriodType.PAY_CYCLE,
                                CycleState.OPEN, END.plusDays(1), true),
                        null));

        AnalyticsSummaryDto result = service.getSummary(WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null);

        assertThat(result.status()).isNotNull();
        assertThat(result.projectionAvailable()).isTrue();
        // savingsRate = (income - projectedPeriodExpenses) / income = (4000 - 3410) / 4000 = 14.75%
        assertThat(result.savingsRate()).isEqualByComparingTo("14.75");
    }
}
