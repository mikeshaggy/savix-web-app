package com.mikeshaggy.backend.regression;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator.ProjectionInput;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator.ProjectionResult;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.analytics.insight.InsightEngine;
import com.mikeshaggy.backend.analytics.insight.InsightResponseDto;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.PeriodTotals;
import com.mikeshaggy.backend.budget.repository.CategoryBudgetRepository;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.ResolvedPeriods;
import com.mikeshaggy.backend.dashboard.dto.DashboardHealthStatus;
import com.mikeshaggy.backend.dashboard.dto.DashboardSummaryDto;
import com.mikeshaggy.backend.dashboard.service.DashboardSummaryService;
import com.mikeshaggy.backend.fixedpayment.dto.FixedProgressDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.RiskIndicatorDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.mikeshaggy.backend.regression.September2026Fixture.CLOCK;
import static com.mikeshaggy.backend.regression.September2026Fixture.LAST_PAY_CYCLE_PERIOD;
import static com.mikeshaggy.backend.regression.September2026Fixture.MONTHLY_PERIOD;
import static com.mikeshaggy.backend.regression.September2026Fixture.PAY_CYCLE_PERIOD;
import static com.mikeshaggy.backend.regression.September2026Fixture.PREVIOUS_MONTH_PERIOD;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_BALANCE;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_ID;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_NAME;
import static com.mikeshaggy.backend.regression.September2026Fixture.TODAY;
import static com.mikeshaggy.backend.regression.September2026Fixture.USER_ID;
import static com.mikeshaggy.backend.regression.September2026Fixture.monthlyProjectionInput;
import static com.mikeshaggy.backend.regression.September2026Fixture.payCycleProjectionInput;
import static com.mikeshaggy.backend.regression.September2026Fixture.projectionInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Documents the legacy linear-extrapolation bug audited on 2026-09-14; delete in §9
 * when the legacy calculator is removed.
 */
@ExtendWith(MockitoExtension.class)
class LegacyForecastCharacterizationTest {

    @Nested
    class Calculator {

        private final SpendingProjectionCalculator calculator = new SpendingProjectionCalculator();

        @Test
        void payCycle_reproducesAuditNumbers() {
            ProjectionResult result = calculator.calculate(payCycleProjectionInput());

            assertThat(result.daysInPeriod()).isEqualTo(30);
            assertThat(result.daysElapsed()).isEqualTo(6);
            assertThat(result.daysRemaining()).isEqualTo(24);
            assertThat(result.variableDailyBurnRate()).isEqualByComparingTo("258.67");
            assertThat(result.projectedVariableRemaining()).isEqualByComparingTo("6208.08");
            assertThat(result.linkedFixedExpensesToDate()).isEqualByComparingTo("1995.90");
            assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("10550.27");
            assertThat(result.projectedEndBalance()).isEqualByComparingTo("-1105.46");
            assertThat(result.safeToSpendToday()).isEqualByComparingTo("-1105.46");
            assertThat(result.safeToSpendPerDay()).isEqualByComparingTo("-46.06");
            assertThat(result.projectionAvailable()).isTrue();
        }

        @Test
        void monthly_reproducesAuditNumbers() {
            ProjectionResult result = calculator.calculate(monthlyProjectionInput());

            assertThat(result.daysElapsed()).isEqualTo(14);
            assertThat(result.daysRemaining()).isEqualTo(16);
            assertThat(result.variableDailyBurnRate()).isEqualByComparingTo("110.86");
            assertThat(result.projectedVariableRemaining()).isEqualByComparingTo("1773.76");
            assertThat(result.projectedPeriodExpenses()).isEqualByComparingTo("5503.66");
            assertThat(result.projectedEndBalance()).isEqualByComparingTo("3941.15");
            assertThat(result.safeToSpendToday()).isEqualByComparingTo("3941.15");
            assertThat(result.safeToSpendPerDay()).isEqualByComparingTo("246.32");
        }

        @Test
        void decomposition_oneInputAtATime() {
            // One ProjectionInput changed at a time, cumulative from the shipped PAY_CYCLE
            // scenario. daysInPeriod is not used by any formula, so widening the window in
            // S1/S2 is harmless — only daysElapsed/daysRemaining/remainingFixed matter.
            //
            // NOTE (BQ-1, resolved 2026-09-14, option A): the plan/audit quote step 1/2 deltas
            // of +3547.48 / +886.84. The calculator rounds variableDailyBurnRate to 2dp
            // (CalculationUtils.SCALE/ROUNDING) BEFORE multiplying by daysRemaining
            // (110.86 vs the unrounded 110.8586), which yields +3547.44 / +886.88 here. Step 3
            // and the total swing reproduce the plan/audit exactly. This test asserts the
            // calculator's real output, not the plan's literal figures.
            BigDecimal s0 = calculator.calculate(payCycleProjectionInput()).safeToSpendToday();
            BigDecimal s1 = calculator.calculate(projectionInput(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 8), new BigDecimal("794.27")))
                    .safeToSpendToday();
            BigDecimal s2 = calculator.calculate(projectionInput(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), new BigDecimal("794.27")))
                    .safeToSpendToday();
            BigDecimal s3 = calculator.calculate(monthlyProjectionInput()).safeToSpendToday();

            assertThat(s0).isEqualByComparingTo("-1105.46");
            assertThat(s1).isEqualByComparingTo("2441.98");
            assertThat(s2).isEqualByComparingTo("3328.86");
            assertThat(s3).isEqualByComparingTo("3941.15");

            assertThat(s1.subtract(s0)).isEqualByComparingTo("3547.44");
            assertThat(s2.subtract(s1)).isEqualByComparingTo("886.88");
            assertThat(s3.subtract(s2)).isEqualByComparingTo("612.29");
            assertThat(s3.subtract(s0)).isEqualByComparingTo("5046.61");
        }
    }

    @Nested
    class DashboardStatus {

        @Mock
        private PeriodService periodService;

        @Mock
        private WalletService walletService;

        @Mock
        private AnalyticsTransactionQueryService transactionQueryService;

        @Mock
        private FixedPaymentDashboardService fixedPaymentDashboardService;

        @Mock
        private InsightEngine insightEngine;

        @Mock
        private CategoryAggregationService categoryAggregationService;

        @Mock
        private CategoryBudgetRepository categoryBudgetRepository;

        private DashboardSummaryService dashboardSummaryService() {
            SpendingProjectionService spendingProjectionService = new SpendingProjectionService(
                    transactionQueryService,
                    walletService,
                    periodService,
                    fixedPaymentDashboardService,
                    new SpendingProjectionCalculator(),
                    CLOCK);
            return new DashboardSummaryService(
                    periodService,
                    walletService,
                    transactionQueryService,
                    spendingProjectionService,
                    fixedPaymentDashboardService,
                    insightEngine,
                    categoryAggregationService,
                    categoryBudgetRepository,
                    CLOCK);
        }

        private Wallet salaryWallet() {
            return Wallet.builder()
                    .id(SALARY_WALLET_ID)
                    .name(SALARY_WALLET_NAME)
                    .balance(SALARY_WALLET_BALANCE)
                    .build();
        }

        @Test
        void payCycle_isDanger() {
            LocalDate cycleStart = LocalDate.of(2026, 9, 9);
            LocalDate cycleEnd = LocalDate.of(2026, 10, 8);
            LocalDate compareStart = LocalDate.of(2026, 8, 10);
            LocalDate compareEnd = LocalDate.of(2026, 8, 15);

            when(walletService.getWalletEntityByIdForUser(SALARY_WALLET_ID, USER_ID))
                    .thenReturn(salaryWallet());
            when(periodService.resolvePeriods(PeriodType.PAY_CYCLE, SALARY_WALLET_ID, USER_ID, null, null))
                    .thenReturn(new ResolvedPeriods(PAY_CYCLE_PERIOD, LAST_PAY_CYCLE_PERIOD));
            when(transactionQueryService.totals(SALARY_WALLET_ID, USER_ID, cycleStart, TODAY))
                    .thenReturn(new PeriodTotals(new BigDecimal("9444.81"), new BigDecimal("3547.92")));
            when(transactionQueryService.totals(SALARY_WALLET_ID, USER_ID, compareStart, compareEnd))
                    .thenReturn(new PeriodTotals(new BigDecimal("1736.52"), new BigDecimal("3417.69")));
            when(transactionQueryService.sum(SALARY_WALLET_ID, USER_ID, cycleStart, TODAY, CategoryType.INCOME))
                    .thenReturn(new BigDecimal("9444.81"));
            when(transactionQueryService.sum(SALARY_WALLET_ID, USER_ID, cycleStart, cycleEnd, CategoryType.INCOME))
                    .thenReturn(new BigDecimal("9444.81"));
            when(transactionQueryService.sum(SALARY_WALLET_ID, USER_ID, cycleStart, TODAY, CategoryType.EXPENSE))
                    .thenReturn(new BigDecimal("3547.92"));
            when(transactionQueryService.sumUnlinked(SALARY_WALLET_ID, USER_ID, cycleStart, TODAY, CategoryType.EXPENSE))
                    .thenReturn(new BigDecimal("1552.02"));
            when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                    any(), any(Wallet.class), eq(USER_ID), eq(TODAY)))
                    .thenReturn(payCycleFixedTile());
            when(insightEngine.getInsightsForWindow(
                    any(Wallet.class), eq(USER_ID), any(), any(), eq(TODAY), any()))
                    .thenReturn(new InsightResponseDto(cycleStart, TODAY, List.of()));
            when(categoryAggregationService.aggregateExpenses(
                    SALARY_WALLET_ID, USER_ID, cycleStart, TODAY,
                    com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES))
                    .thenReturn(new CategoryAggregationResult(new BigDecimal("3547.92"), List.of()));
            when(categoryBudgetRepository.findActiveByWalletIdAndUserId(SALARY_WALLET_ID, USER_ID))
                    .thenReturn(List.of());

            DashboardSummaryDto result = dashboardSummaryService().getSummary(
                    SALARY_WALLET_ID, USER_ID, PeriodType.PAY_CYCLE, null, null, null,
                    com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

            assertThat(result.cycleHealth().status()).isEqualTo(DashboardHealthStatus.DANGER);
            assertThat(result.cycleHealth().safeToSpend()).isEqualByComparingTo("-1105.46");
            assertThat(result.cycleHealth().safeToSpendPerDay()).isEqualByComparingTo("-46.06");
            assertThat(result.cycleHealth().projectedEndBalance()).isEqualByComparingTo("-1105.46");
            assertThat(result.cycleHealth().spendingPaceDeltaAmount()).isEqualByComparingTo("130.23");
            assertThat(result.cycleHealth().spendingPaceDeltaPercent()).isEqualByComparingTo("3.81");
            assertThat(result.period().daysInPeriod()).isEqualTo(30);
            assertThat(result.period().daysElapsed()).isEqualTo(6);
            assertThat(result.period().daysRemaining()).isEqualTo(24);
            assertThat(result.period().endDate()).isEqualTo(cycleEnd);
            assertThat(result.fixedPayments().balanceAfterRemainingFixedPayments()).isEqualByComparingTo("5102.62");
        }

        @Test
        void monthly_isOnTrack() {
            LocalDate monthStart = LocalDate.of(2026, 9, 1);
            LocalDate monthEnd = LocalDate.of(2026, 9, 30);
            LocalDate compareStart = LocalDate.of(2026, 8, 1);
            LocalDate compareEnd = LocalDate.of(2026, 8, 14);

            when(walletService.getWalletEntityByIdForUser(SALARY_WALLET_ID, USER_ID))
                    .thenReturn(salaryWallet());
            when(periodService.resolvePeriods(PeriodType.MONTHLY, SALARY_WALLET_ID, USER_ID, null, null))
                    .thenReturn(new ResolvedPeriods(MONTHLY_PERIOD, PREVIOUS_MONTH_PERIOD));
            when(transactionQueryService.totals(SALARY_WALLET_ID, USER_ID, monthStart, TODAY))
                    .thenReturn(new PeriodTotals(new BigDecimal("9444.81"), new BigDecimal("3547.92")));
            when(transactionQueryService.totals(SALARY_WALLET_ID, USER_ID, compareStart, compareEnd))
                    .thenReturn(new PeriodTotals(new BigDecimal("1736.52"), new BigDecimal("4918.14")));
            when(transactionQueryService.sum(SALARY_WALLET_ID, USER_ID, monthStart, TODAY, CategoryType.INCOME))
                    .thenReturn(new BigDecimal("9444.81"));
            when(transactionQueryService.sum(SALARY_WALLET_ID, USER_ID, monthStart, monthEnd, CategoryType.INCOME))
                    .thenReturn(new BigDecimal("9444.81"));
            when(transactionQueryService.sum(SALARY_WALLET_ID, USER_ID, monthStart, TODAY, CategoryType.EXPENSE))
                    .thenReturn(new BigDecimal("3547.92"));
            when(transactionQueryService.sumUnlinked(SALARY_WALLET_ID, USER_ID, monthStart, TODAY, CategoryType.EXPENSE))
                    .thenReturn(new BigDecimal("1552.02"));
            when(fixedPaymentDashboardService.getFixedPaymentsTileData(
                    any(), any(Wallet.class), eq(USER_ID), eq(TODAY)))
                    .thenReturn(monthlyFixedTile());
            when(insightEngine.getInsightsForWindow(
                    any(Wallet.class), eq(USER_ID), any(), any(), eq(TODAY), any()))
                    .thenReturn(new InsightResponseDto(monthStart, TODAY, List.of()));
            when(categoryAggregationService.aggregateExpenses(
                    SALARY_WALLET_ID, USER_ID, monthStart, TODAY,
                    com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES))
                    .thenReturn(new CategoryAggregationResult(new BigDecimal("3547.92"), List.of()));
            when(categoryBudgetRepository.findActiveByWalletIdAndUserId(SALARY_WALLET_ID, USER_ID))
                    .thenReturn(List.of());

            DashboardSummaryDto result = dashboardSummaryService().getSummary(
                    SALARY_WALLET_ID, USER_ID, PeriodType.MONTHLY, null, null, null,
                    com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES);

            assertThat(result.cycleHealth().status()).isEqualTo(DashboardHealthStatus.ON_TRACK);
            assertThat(result.cycleHealth().safeToSpend()).isEqualByComparingTo("3941.15");
            assertThat(result.cycleHealth().safeToSpendPerDay()).isEqualByComparingTo("246.32");
            assertThat(result.cycleHealth().projectedEndBalance()).isEqualByComparingTo("3941.15");
            assertThat(result.cycleHealth().spendingPaceDeltaAmount()).isEqualByComparingTo("-1370.22");
            assertThat(result.cycleHealth().spendingPaceDeltaPercent()).isEqualByComparingTo("-27.86");
            assertThat(result.period().daysInPeriod()).isEqualTo(30);
            assertThat(result.period().daysElapsed()).isEqualTo(14);
            assertThat(result.period().daysRemaining()).isEqualTo(16);
            assertThat(result.fixedPayments().balanceAfterRemainingFixedPayments()).isEqualByComparingTo("5714.91");
        }

        private FixedTransactionsTileDto payCycleFixedTile() {
            return new FixedTransactionsTileDto(
                    LocalDate.of(2026, 9, 9),
                    LocalDate.of(2026, 10, 8),
                    LocalDate.of(2026, 10, 8),
                    new FixedSummaryDto(
                            new BigDecimal("2810.26"), 8,
                            new BigDecimal("2015.99"), 3,
                            new BigDecimal("794.27"), 5,
                            BigDecimal.ZERO, 0,
                            new BigDecimal("38.00")),
                    new FixedProgressDto(3, 8, new BigDecimal("37.50"),
                            LocalDate.of(2026, 9, 20), "FX_UTILITY_A", "FX_INSTALMENT",
                            new BigDecimal("408.30"), 8),
                    SALARY_WALLET_BALANCE,
                    new BigDecimal("5102.62"),
                    new RiskIndicatorDto(false, null),
                    List.of(),
                    List.of(),
                    List.of());
        }

        private FixedTransactionsTileDto monthlyFixedTile() {
            return new FixedTransactionsTileDto(
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 30),
                    LocalDate.of(2026, 9, 30),
                    new FixedSummaryDto(
                            new BigDecimal("2232.96"), 6,
                            new BigDecimal("2050.98"), 4,
                            new BigDecimal("181.98"), 2,
                            BigDecimal.ZERO, 0,
                            new BigDecimal("38.00")),
                    new FixedProgressDto(4, 6, new BigDecimal("66.67"),
                            LocalDate.of(2026, 9, 20), "FX_UTILITY_A", "FX_MEMBERSHIP",
                            new BigDecimal("169.00"), 6),
                    SALARY_WALLET_BALANCE,
                    new BigDecimal("5714.91"),
                    new RiskIndicatorDto(false, null),
                    List.of(),
                    List.of(),
                    List.of());
        }
    }
}
