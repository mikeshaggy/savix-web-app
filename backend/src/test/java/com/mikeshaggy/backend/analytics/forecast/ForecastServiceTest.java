package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleContribution;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.HistoricalBaseline;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.VariableExpense;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedProgressDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.RiskIndicatorDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.regression.September2026Fixture;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.mikeshaggy.backend.regression.September2026Fixture.CLOCK;
import static com.mikeshaggy.backend.regression.September2026Fixture.CURRENT_CYCLE_START;
import static com.mikeshaggy.backend.regression.September2026Fixture.EXPECTED_NEXT_PAYDAY;
import static com.mikeshaggy.backend.regression.September2026Fixture.LEGACY_CURRENT_CYCLE_END;
import static com.mikeshaggy.backend.regression.September2026Fixture.PAY_CYCLE_PERIOD_V2;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_BALANCE;
import static com.mikeshaggy.backend.regression.September2026Fixture.SALARY_WALLET_ID;
import static com.mikeshaggy.backend.regression.September2026Fixture.TODAY;
import static com.mikeshaggy.backend.regression.September2026Fixture.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Stage 4.7 wiring of {@link ForecastService} on the synthetic Sep 14 fixture: applicability, the Stage 3
 * committed source, the single pace-eligible series and the mapped {@link ForecastV2Dto}.
 */
@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    /** Sep 9 → Oct 8: D = 30, d = 6 on Sep 14, R = 24. */
    private static final int CYCLE_LENGTH = 30;
    private static final int DAY_INDEX = 6;
    private static final int DAYS_REMAINING = 24;

    @Mock
    private AnalyticsTransactionQueryService transactionQueryService;
    @Mock
    private FixedPaymentDashboardService fixedPaymentDashboardService;
    @Mock
    private HistoricalVariableSpendService historicalVariableSpendService;

    private final ForecastV2Calculator calculator = new ForecastV2Calculator();
    private ForecastService service;
    private Wallet salaryWallet;

    @BeforeEach
    void setUp() {
        service = service(calculator, new OneOffDetector(calculator));
        salaryWallet = Wallet.builder().id(SALARY_WALLET_ID).name("FX_SALARY_WALLET")
                .balance(SALARY_WALLET_BALANCE).build();
    }

    private ForecastService service(ForecastV2Calculator calc, OneOffDetector detector) {
        return new ForecastService(transactionQueryService, fixedPaymentDashboardService,
                historicalVariableSpendService, calc, detector, CLOCK);
    }

    // --- applicability ------------------------------------------------------------------------------

    @Nested
    class Applicability {

        @Test
        void monthlyHasNoForecast() {
            assertThat(service.forecast(salaryWallet, USER_ID, September2026Fixture.MONTHLY_PERIOD, TODAY, null)).isNull();
            verifyNoInteractions(transactionQueryService, fixedPaymentDashboardService, historicalVariableSpendService);
        }

        @Test
        void customHasNoForecast() {
            PeriodDto custom = PeriodDto.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 14),
                    LocalDate.of(2026, 9, 15), PeriodType.CUSTOM);
            assertThat(service.forecast(salaryWallet, USER_ID, custom, TODAY, null)).isNull();
            verifyNoInteractions(transactionQueryService, fixedPaymentDashboardService, historicalVariableSpendService);
        }

        @Test
        void lastPayCycleHasNoForecast() {
            assertThat(service.forecast(salaryWallet, USER_ID, September2026Fixture.LAST_PAY_CYCLE_PERIOD_V2, TODAY, null))
                    .isNull();
            verifyNoInteractions(transactionQueryService, fixedPaymentDashboardService, historicalVariableSpendService);
        }

        @Test
        void nonSalaryWalletHasNoForecast() {
            // pay-cycle-v2 answers a savings wallet with the calendar month typed MONTHLY; a PAY_CYCLE period that
            // is explicitly not the salary wallet is rejected too
            Wallet savings = Wallet.builder().id(September2026Fixture.SAVINGS_WALLET_ID)
                    .balance(September2026Fixture.SAVINGS_WALLET_BALANCE).build();
            assertThat(service.forecast(savings, USER_ID, September2026Fixture.SAVINGS_WALLET_MONTHLY_PERIOD, TODAY, null))
                    .isNull();
            PeriodDto notSalary = new PeriodDto(CURRENT_CYCLE_START, LEGACY_CURRENT_CYCLE_END, EXPECTED_NEXT_PAYDAY,
                    PeriodType.PAY_CYCLE, CycleState.OPEN, EXPECTED_NEXT_PAYDAY, false);
            assertThat(service.forecast(savings, USER_ID, notSalary, TODAY, null)).isNull();
            verifyNoInteractions(transactionQueryService, fixedPaymentDashboardService, historicalVariableSpendService);
        }

        @Test
        void legacyResolvedCycleWithoutStateHasNoForecast() {
            // pay-cycle-v2 off: the PAY_CYCLE period carries no cycle metadata — no forecast is synthesised
            assertThat(service.forecast(salaryWallet, USER_ID, September2026Fixture.PAY_CYCLE_PERIOD, TODAY, null)).isNull();
            PeriodDto closed = new PeriodDto(CURRENT_CYCLE_START, LEGACY_CURRENT_CYCLE_END, EXPECTED_NEXT_PAYDAY,
                    PeriodType.PAY_CYCLE, CycleState.CLOSED, null, true);
            assertThat(service.forecast(salaryWallet, USER_ID, closed, TODAY, null)).isNull();
            assertThat(service.isApplicable(null)).isFalse();
            verifyNoInteractions(transactionQueryService, fixedPaymentDashboardService, historicalVariableSpendService);
        }

        @Test
        void awaitingSalaryReturnsTheObligationShellWithoutAVerdict() {
            // expected payday Sep 10 passed; viewing on Sep 14 with the tile window extended through today
            PeriodDto awaiting = new PeriodDto(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10),
                    PeriodType.PAY_CYCLE, CycleState.AWAITING_SALARY, LocalDate.of(2026, 9, 10), true);
            FixedTransactionsTileDto tile = tile(
                    List.of(row(1L, "FX_UTILITY_A", "76.98", LocalDate.of(2026, 9, 5), OccurrenceStatus.OVERDUE, FixedOccurrenceBucket.OVERDUE)),
                    List.of(row(2L, "FX_SUB_A", "105.00", LocalDate.of(2026, 9, 14), OccurrenceStatus.PENDING, FixedOccurrenceBucket.DUE_SOON)),
                    List.of(row(3L, "FX_RENT", "1900.00", LocalDate.of(2026, 8, 10), OccurrenceStatus.PAID, FixedOccurrenceBucket.PAID_THIS_CYCLE)),
                    List.of(),
                    "105.00", "76.98");

            ForecastV2Dto forecast = service.forecast(salaryWallet, USER_ID, awaiting, TODAY, tile);

            assertThat(forecast).isNotNull();
            assertThat(forecast.status()).isNull();
            assertThat(forecast.confidence()).isNull();
            assertThat(forecast.projectionReason()).isEqualTo(SpendingProjectionCalculator.REASON_AWAITING_SALARY);
            assertThat(forecast.committed()).isEqualByComparingTo("181.98");
            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("5714.91"); // 5896.89 − 181.98
            assertThat(forecast.committedOccurrences()).extracting(CommittedOccurrenceDto::occurrenceId).containsExactly(1L, 2L);
            // no remaining horizon: nothing is projected, nothing invented
            assertThat(forecast.discretionaryPerDay()).isNull();
            assertThat(forecast.expectedVariableRemaining()).isNull();
            assertThat(forecast.expectedVariableRemainingLow()).isNull();
            assertThat(forecast.expectedVariableRemainingHigh()).isNull();
            assertThat(forecast.expectedEndBalanceTypical()).isNull();
            assertThat(forecast.expectedEndBalanceLow()).isNull();
            assertThat(forecast.expectedEndBalanceHigh()).isNull();
            assertThat(forecast.trimmedDailyPace()).isNull();
            assertThat(forecast.rawDailyBurnRate()).isNull();
            assertThat(forecast.historicalTypicalPerDay()).isNull();
            assertThat(forecast.historyWeight()).isNull();
            assertThat(forecast.baselineCyclesUsed()).isNull();
            assertThat(forecast.baselineCycles()).isEmpty();
            assertThat(forecast.oneOffs()).isEmpty();
            verifyNoInteractions(transactionQueryService, historicalVariableSpendService);
        }
    }

    // --- open cycle: Sep 14 fixture ------------------------------------------------------------------

    @Nested
    class OpenCycleSeptember14 {

        @BeforeEach
        void fixtureQueries() {
            lenient().when(transactionQueryService.dailyVariableTotals(SALARY_WALLET_ID, USER_ID, CURRENT_CYCLE_START, TODAY))
                    .thenReturn(fixtureDaily("443.00"));
            lenient().when(transactionQueryService.unlinkedExpenses(SALARY_WALLET_ID, USER_ID, CURRENT_CYCLE_START, TODAY))
                    .thenReturn(fixtureCandidates(false));
            lenient().when(historicalVariableSpendService.baseline(eq(USER_ID), eq(SALARY_WALLET_ID), any(PayCycle.class),
                            eq(DAY_INDEX), eq(DAYS_REMAINING)))
                    .thenReturn(fixtureBaseline());
        }

        @Test
        void reachesTheCalculatorSemanticsEndToEnd() {
            ForecastV2Dto forecast = service.forecast(salaryWallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, fixtureTile());

            assertThat(forecast).isNotNull();
            assertThat(forecast.status()).isEqualTo(ForecastStatus.FINE);
            assertThat(forecast.projectionReason()).isNull();
            assertThat(forecast.confidence()).isEqualTo(ForecastConfidence.HIGH);
            assertThat(forecast.committed()).isEqualByComparingTo(September2026Fixture.REMAINING_FIXED_PAY_CYCLE);   // 794.27
            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("5102.62");
            assertThat(forecast.discretionaryPerDay()).isEqualByComparingTo("212.61");
            assertThat(forecast.trimmedDailyPace()).isEqualByComparingTo("326.20");
            assertThat(forecast.rawDailyBurnRate()).isEqualByComparingTo("258.67");
            assertThat(forecast.historyWeight()).isEqualByComparingTo("0.80");
            assertThat(forecast.historicalTypicalPerDay()).isEqualByComparingTo("120.02");
            assertThat(forecast.baselineCyclesUsed()).isEqualTo(3);
            assertThat(forecast.expectedVariableRemaining()).isEqualByComparingTo("3870.14");
            assertThat(forecast.expectedVariableRemainingLow()).isEqualByComparingTo("4677.20");   // pessimistic
            assertThat(forecast.expectedVariableRemainingHigh()).isEqualByComparingTo("3094.32");  // optimistic
            assertThat(forecast.expectedEndBalanceTypical()).isEqualByComparingTo("1232.48")
                    .isBetween(new BigDecimal("800.00"), new BigDecimal("2800.00"));
            assertThat(forecast.expectedEndBalanceLow()).isEqualByComparingTo("425.42");
            assertThat(forecast.expectedEndBalanceHigh()).isEqualByComparingTo("2008.30");

            // baseline cycles carried through as read models
            assertThat(forecast.baselineCycles()).hasSize(3);
            BaselineCycleDto august = forecast.baselineCycles().getFirst();
            assertThat(august.start()).isEqualTo(LocalDate.of(2026, 8, 10));
            assertThat(august.end()).isEqualTo(LocalDate.of(2026, 9, 8));
            assertThat(august.lengthDays()).isEqualTo(30);
            assertThat(august.variableTotal()).isEqualByComparingTo("3600.60");
            assertThat(august.remainingFromDay()).isEqualByComparingTo("2880.48");
            assertThat(august.normalised()).isEqualByComparingTo("2880.48");

            // the 300 PLN repayment: not excluded → positive impact, share against gross 1552.02
            assertThat(forecast.oneOffs()).hasSize(1);
            OneOffDto repayment = forecast.oneOffs().getFirst();
            assertThat(repayment.transactionId()).isEqualTo(300L);
            assertThat(repayment.amount()).isEqualByComparingTo("300.00");
            assertThat(repayment.date()).isEqualTo(September2026Fixture.ONE_OFF_DATE);
            assertThat(repayment.title()).isEqualTo(September2026Fixture.ONE_OFF_TITLE);
            assertThat(repayment.categoryName()).isEqualTo(September2026Fixture.ONE_OFF_CATEGORY);
            assertThat(repayment.shareOfVariable()).isEqualByComparingTo("19.33");
            assertThat(repayment.excluded()).isFalse();
            assertThat(repayment.impactOnExpectedVariableRemaining()).isEqualByComparingTo("462.33");
            assertThat(repayment.impactOnExpectedEndBalance()).isEqualByComparingTo("462.33");

            // committed occurrences: the five pending rows by due date, nothing else
            assertThat(forecast.committedOccurrences()).extracting(CommittedOccurrenceDto::title)
                    .containsExactly("FX_UTILITY_A", "FX_SUB_A", "FX_MEMBERSHIP", "FX_SUB_B", "FX_INSTALMENT");
            assertThat(forecast.committedOccurrences()).allSatisfy(o -> assertThat(o.bucket()).isIn(
                    FixedOccurrenceBucket.DUE_SOON, FixedOccurrenceBucket.LATER_THIS_CYCLE));
        }

        @Test
        void committedComesFromTheStageThreeTileRowsAndAgreesWithItsSummary() {
            // tile with every bucket: OVERDUE + DUE_SOON + LATER count; AFTER_PAYDAY and PAID never
            FixedTransactionsTileDto tile = tile(
                    List.of(row(10L, "FX_LATE", "50.00", LocalDate.of(2026, 9, 12), OccurrenceStatus.OVERDUE, FixedOccurrenceBucket.OVERDUE)),
                    List.of(row(11L, "FX_SOON", "100.00", LocalDate.of(2026, 9, 18), OccurrenceStatus.PENDING, FixedOccurrenceBucket.DUE_SOON),
                            row(12L, "FX_LATER", "200.00", LocalDate.of(2026, 10, 5), OccurrenceStatus.PENDING, FixedOccurrenceBucket.LATER_THIS_CYCLE)),
                    List.of(row(13L, "FX_RENT", "1900.00", LocalDate.of(2026, 9, 10), OccurrenceStatus.PAID, FixedOccurrenceBucket.PAID_THIS_CYCLE)),
                    List.of(row(14L, "FX_PAYDAY_DUE", "4300.00", EXPECTED_NEXT_PAYDAY, OccurrenceStatus.PENDING, FixedOccurrenceBucket.AFTER_PAYDAY)),
                    "300.00", "50.00");

            ForecastV2Dto forecast = service.forecast(salaryWallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, tile);

            BigDecimal tileCommitted = tile.summary().remainingAmount().add(tile.summary().overdueAmount());
            assertThat(forecast.committed()).isEqualByComparingTo("350.00").isEqualByComparingTo(tileCommitted);
            assertThat(forecast.committedOccurrences()).extracting(CommittedOccurrenceDto::occurrenceId)
                    .containsExactly(10L, 11L, 12L);
            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("5546.89");   // 5896.89 − 350.00
            // the 4,300 due on payday would be SHORT if it entered the affordability walk; it does not
            assertThat(forecast.status()).isEqualTo(ForecastStatus.FINE);
            // nothing was re-queried: the tile handed in is the committed source
            verify(fixedPaymentDashboardService, never()).getFixedPaymentsTileData(any(), any(Wallet.class), any(), any());
        }

        @Test
        void loadsTheTileThroughTheStageThreeServiceWhenTheCallerHasNone() {
            when(fixedPaymentDashboardService.getFixedPaymentsTileData(PAY_CYCLE_PERIOD_V2, salaryWallet, USER_ID, TODAY))
                    .thenReturn(fixtureTile());

            ForecastV2Dto forecast = service.forecast(salaryWallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, null);

            assertThat(forecast.committed()).isEqualByComparingTo("794.27");
            verify(fixedPaymentDashboardService, times(1)).getFixedPaymentsTileData(any(), any(Wallet.class), any(), any());
        }

        @Test
        void defaultsToTheClockWhenNoAsOfDateIsGiven() {
            ForecastV2Dto forecast = service.forecast(salaryWallet, USER_ID, PAY_CYCLE_PERIOD_V2, null, fixtureTile());

            assertThat(forecast.discretionaryNow()).isEqualByComparingTo("5102.62");
            verify(transactionQueryService).dailyVariableTotals(SALARY_WALLET_ID, USER_ID, CURRENT_CYCLE_START, TODAY);
        }

        @Test
        void theSamePaceEligibleSeriesFeedsCurrentPaceTheBaseForecastAndTheOneOffImpacts() {
            ForecastV2Calculator spiedCalculator = spy(new ForecastV2Calculator());
            OneOffDetector detector = mock(OneOffDetector.class);
            when(detector.detect(any(), any(), any())).thenReturn(List.of());
            ForecastService wired = service(spiedCalculator, detector);
            List<DailyTotal> daily = fixtureDaily("443.00");
            when(transactionQueryService.dailyVariableTotals(SALARY_WALLET_ID, USER_ID, CURRENT_CYCLE_START, TODAY))
                    .thenReturn(daily);

            wired.forecast(salaryWallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, fixtureTile());

            // loaded once …
            verify(transactionQueryService, times(1)).dailyVariableTotals(any(), any(), any(), any());
            // … and the very same list reaches the pace, the forecast input and the detector
            verify(spiedCalculator).currentPace(same(daily), eq(DAYS_REMAINING));
            ArgumentCaptor<ForecastV2Calculator.ForecastInput> input = ArgumentCaptor.forClass(ForecastV2Calculator.ForecastInput.class);
            verify(spiedCalculator).forecast(input.capture());
            assertThat(input.getValue().dayIndex()).isEqualTo(DAY_INDEX);
            assertThat(input.getValue().cycleLengthDays()).isEqualTo(CYCLE_LENGTH);
            assertThat(input.getValue().daysRemaining()).isEqualTo(DAYS_REMAINING);
            assertThat(input.getValue().currentPace().daysElapsed()).isEqualTo(DAY_INDEX);
            verify(detector).detect(any(), same(daily), same(input.getValue()));
            // the baseline is asked for the same d / R and the cycle the period describes
            ArgumentCaptor<PayCycle> cycle = ArgumentCaptor.forClass(PayCycle.class);
            verify(historicalVariableSpendService).baseline(eq(USER_ID), eq(SALARY_WALLET_ID), cycle.capture(),
                    eq(DAY_INDEX), eq(DAYS_REMAINING));
            assertThat(cycle.getValue().start()).isEqualTo(CURRENT_CYCLE_START);
            assertThat(cycle.getValue().end()).isEqualTo(LEGACY_CURRENT_CYCLE_END);
            assertThat(cycle.getValue().state()).isEqualTo(CycleState.OPEN);
        }

        @Test
        void excludedRowsStayOutOfThePaceButRemainExplainable() {
            // the repayment is excluded from the pace: the eligible series holds 143.00 on Sep 13, the gross
            // candidate set still lists the 300 (excluded = true)
            when(transactionQueryService.dailyVariableTotals(SALARY_WALLET_ID, USER_ID, CURRENT_CYCLE_START, TODAY))
                    .thenReturn(fixtureDaily("143.00"));
            when(transactionQueryService.unlinkedExpenses(SALARY_WALLET_ID, USER_ID, CURRENT_CYCLE_START, TODAY))
                    .thenReturn(fixtureCandidates(true));

            ForecastV2Dto forecast = service.forecast(salaryWallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, fixtureTile());

            // pace math never saw the 300: median (143.00 + 316.76) / 2
            assertThat(forecast.trimmedDailyPace()).isEqualByComparingTo("229.88");
            assertThat(forecast.rawDailyBurnRate()).isEqualByComparingTo("208.67");   // 1252.02 / 6
            assertThat(forecast.expectedVariableRemaining()).isEqualByComparingTo("3407.81");
            assertThat(forecast.expectedEndBalanceTypical()).isEqualByComparingTo("1694.81");
            // still explained, flagged excluded, zero incremental impact, share unchanged (gross denominator)
            assertThat(forecast.oneOffs()).hasSize(1);
            OneOffDto repayment = forecast.oneOffs().getFirst();
            assertThat(repayment.transactionId()).isEqualTo(300L);
            assertThat(repayment.excluded()).isTrue();
            assertThat(repayment.shareOfVariable()).isEqualByComparingTo("19.33");
            assertThat(repayment.impactOnExpectedVariableRemaining()).isEqualByComparingTo("0.00");
            assertThat(repayment.impactOnExpectedEndBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        void zeroHistoryUserGetsThePaceOnlyForecastWithLowConfidence() {
            when(historicalVariableSpendService.baseline(eq(USER_ID), eq(SALARY_WALLET_ID), any(PayCycle.class), anyInt(), anyInt()))
                    .thenReturn(new HistoricalBaseline(0, null, null, null, List.of(), null));

            ForecastV2Dto forecast = service.forecast(salaryWallet, USER_ID, PAY_CYCLE_PERIOD_V2, TODAY, fixtureTile());

            assertThat(forecast.historyWeight()).isEqualByComparingTo("0");
            assertThat(forecast.confidence()).isEqualTo(ForecastConfidence.LOW);
            assertThat(forecast.baselineCyclesUsed()).isZero();
            assertThat(forecast.baselineCycles()).isEmpty();
            assertThat(forecast.historicalTypicalPerDay()).isNull();
            assertThat(forecast.expectedVariableRemaining()).isEqualByComparingTo("7828.80");   // 326.20 × 24
            assertThat(forecast.expectedEndBalanceTypical()).isEqualByComparingTo("-2726.18");
            // pessimistic 9,786.00 prorated to Oct 8 leaves nothing for the 408.30 instalment → SHORT (4.5 walk)
            assertThat(forecast.status()).isEqualTo(ForecastStatus.SHORT);
        }

        @Test
        void anExplicitAsOfDatePastTheCycleEndHasNoRemainingHorizon() {
            LocalDate end = LEGACY_CURRENT_CYCLE_END;
            List<DailyTotal> full = new ArrayList<>();
            for (LocalDate day = CURRENT_CYCLE_START; !day.isAfter(end); day = day.plusDays(1)) {
                full.add(new DailyTotal(day, new BigDecimal("100.00")));
            }
            when(transactionQueryService.dailyVariableTotals(SALARY_WALLET_ID, USER_ID, CURRENT_CYCLE_START, end)).thenReturn(full);
            when(transactionQueryService.unlinkedExpenses(SALARY_WALLET_ID, USER_ID, CURRENT_CYCLE_START, end)).thenReturn(List.of());
            when(historicalVariableSpendService.baseline(eq(USER_ID), eq(SALARY_WALLET_ID), any(PayCycle.class), eq(30), eq(0)))
                    .thenReturn(new HistoricalBaseline(0, null, null, null, List.of(), null));

            ForecastV2Dto forecast = service.forecast(salaryWallet, USER_ID, PAY_CYCLE_PERIOD_V2, end.plusDays(3), fixtureTile());

            assertThat(forecast.expectedVariableRemaining()).isEqualByComparingTo("0.00");
            assertThat(forecast.discretionaryPerDay()).isEqualByComparingTo(forecast.discretionaryNow());
        }
    }

    // --- fixture helpers ---------------------------------------------------------------------------

    /** Zero-filled Sep 9..14 pace-eligible series; {@code sep13} is 443.00 with the repayment, 143.00 without. */
    static List<DailyTotal> fixtureDaily(String sep13) {
        String[] amounts = {"94.98", "335.64", "361.64", "316.76", sep13, "0.00"};
        List<DailyTotal> daily = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            daily.add(new DailyTotal(CURRENT_CYCLE_START.plusDays(i), new BigDecimal(amounts[i])));
        }
        return daily;
    }

    /** Gross unlinked expenses Sep 9..14 summing to 1,552.02 (same shape as {@code OneOffDetectorTest}). */
    static List<VariableExpense> fixtureCandidates(boolean repaymentExcluded) {
        LocalDate s = CURRENT_CYCLE_START;
        return List.of(
                tx(1, s, "94.98"),
                tx(2, s.plusDays(1), "180.00"), tx(3, s.plusDays(1), "155.64"),
                tx(4, s.plusDays(2), "190.00"), tx(5, s.plusDays(2), "171.64"),
                tx(6, s.plusDays(3), "160.00"), tx(7, s.plusDays(3), "156.76"),
                tx(8, s.plusDays(4), "143.00"),
                new VariableExpense(300L, September2026Fixture.ONE_OFF_DATE, September2026Fixture.ONE_OFF_TITLE,
                        September2026Fixture.ONE_OFF_CATEGORY, September2026Fixture.ONE_OFF_AMOUNT, repaymentExcluded));
    }

    private static VariableExpense tx(long id, LocalDate date, String amount) {
        return new VariableExpense(id, date, "tx-" + id, "groceries", new BigDecimal(amount), false);
    }

    /** Three closed 30-day cycles; median 2880.48 (≈ 120.02/day × 24), p25 2400.00, p75 3400.00. */
    static HistoricalBaseline fixtureBaseline() {
        List<CycleContribution> perCycle = List.of(
                contribution(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8), "3600.60", "2880.48"),
                contribution(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9), "3000.00", "2400.00"),
                contribution(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9), "4250.00", "3400.00"));
        return new HistoricalBaseline(3, new BigDecimal("2880.48"), new BigDecimal("2400.00"), new BigDecimal("3400.00"),
                perCycle, new BigDecimal("120.02"));
    }

    private static CycleContribution contribution(LocalDate start, LocalDate end, String total, String remaining) {
        // a 30-day cycle at d = 6 has 24 remaining days = R, so normalised == remaining
        return new CycleContribution(PayCycle.closed(USER_ID, SALARY_WALLET_ID, start, end), 30,
                new BigDecimal(total), new BigDecimal(remaining), 24, new BigDecimal(remaining));
    }

    /** The fixture tile: five pending rows (794.27), three paid rows, no overdue, no after-payday rows. */
    static FixedTransactionsTileDto fixtureTile() {
        List<FixedOccurrenceRowDto> upcoming = new ArrayList<>();
        long id = 1;
        for (September2026Fixture.FixedOccurrence o : September2026Fixture.PENDING_OCCURRENCES) {
            upcoming.add(row(id++, o.title(), o.plannedAmount().toPlainString(), o.dueDate(), OccurrenceStatus.PENDING,
                    FixedOccurrenceBucket.of(OccurrenceStatus.PENDING, o.dueDate(), TODAY, LEGACY_CURRENT_CYCLE_END)));
        }
        List<FixedOccurrenceRowDto> paid = new ArrayList<>();
        for (September2026Fixture.FixedOccurrence o : September2026Fixture.PAID_OCCURRENCES) {
            paid.add(row(id++, o.title(), o.plannedAmount().toPlainString(), o.dueDate(), OccurrenceStatus.PAID,
                    FixedOccurrenceBucket.PAID_THIS_CYCLE));
        }
        return tile(List.of(), upcoming, paid, List.of(), "794.27", "0.00");
    }

    static FixedTransactionsTileDto tile(List<FixedOccurrenceRowDto> overdue, List<FixedOccurrenceRowDto> upcoming,
                                         List<FixedOccurrenceRowDto> paid, List<FixedOccurrenceRowDto> afterPayday,
                                         String remainingAmount, String overdueAmount) {
        FixedSummaryDto summary = new FixedSummaryDto(
                new BigDecimal(remainingAmount).add(new BigDecimal(overdueAmount)), upcoming.size() + overdue.size() + paid.size(),
                BigDecimal.ZERO, paid.size(),
                new BigDecimal(remainingAmount), upcoming.size(),
                new BigDecimal(overdueAmount), overdue.size(),
                BigDecimal.ZERO, BigDecimal.ZERO);
        FixedProgressDto progress = new FixedProgressDto(0, 0, BigDecimal.ZERO, null, null, null, null, 0);
        return new FixedTransactionsTileDto(
                CURRENT_CYCLE_START, LEGACY_CURRENT_CYCLE_END, EXPECTED_NEXT_PAYDAY, EXPECTED_NEXT_PAYDAY, CycleState.OPEN,
                summary, progress, SALARY_WALLET_BALANCE, SALARY_WALLET_BALANCE, new RiskIndicatorDto(false, null),
                overdue, upcoming, paid, null, afterPayday);
    }

    static FixedOccurrenceRowDto row(Long id, String title, String amount, LocalDate dueDate, OccurrenceStatus status,
                                     FixedOccurrenceBucket bucket) {
        return new FixedOccurrenceRowDto(id, 1, title, 1, "cat", null, SALARY_WALLET_ID, new BigDecimal(amount),
                status == OccurrenceStatus.PAID ? new BigDecimal(amount) : null, dueDate, status, 0, null, null, null,
                null, null, bucket);
    }
}
