package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.dashboard.dto.DashboardCycleSoFarDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardSavingsDto;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWallet;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWalletFlow;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CycleSummary;
import com.mikeshaggy.backend.transfer.service.TransferSummaryService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Stage 6.3 — {@code cycleSoFar} and {@code savings}. The Stage 4 baseline is the real
 * {@link HistoricalVariableSpendService} over mocked cycles and pace-eligible daily series.
 */
@ExtendWith(MockitoExtension.class)
class DashboardV2SectionsTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Integer DAILY_ID = 1;
    private static final Integer SAVINGS_ID = 2;
    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final LocalDate END = LocalDate.of(2026, 10, 8);
    /** Day 6 of the cycle. */
    private static final LocalDate CUTOFF = LocalDate.of(2026, 9, 14);

    private static final PayCycle C1 = PayCycle.closed(USER_ID, DAILY_ID, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8));
    private static final PayCycle C2 = PayCycle.closed(USER_ID, DAILY_ID, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9));
    private static final PayCycle C3 = PayCycle.closed(USER_ID, DAILY_ID, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 7, 9));

    @Mock private AnalyticsTransactionQueryService transactionQueryService;
    @Mock private PayCycleService payCycleService;
    @Mock private TransferSummaryService transferSummaryService;
    @Mock private WalletRepository walletRepository;

    private HistoricalVariableSpendService historicalVariableSpendService;
    private DashboardV2Sections sections;
    private final Wallet daily = Wallet.builder().id(DAILY_ID).name("daily").balance(new BigDecimal("5896.89")).build();

    @BeforeEach
    void setUp() {
        historicalVariableSpendService = new HistoricalVariableSpendService(payCycleService, transactionQueryService);
        sections = new DashboardV2Sections(
                transactionQueryService, historicalVariableSpendService, transferSummaryService, walletRepository);
    }

    @Nested
    class CycleSoFar {

        @Test
        void typicalIsTheStage4BaselineAtTodaysCycleDayAndDeltaIsVariableMinusTypical() {
            stubHistory();
            // current cycle day 1..6: Σ 380.00 pace-eligible
            stubDaily(START, CUTOFF, "100.00", "0.00", "80.00", "0.00", "150.00", "50.00");

            DashboardCycleSoFarDto result = sections.cycleSoFar(daily, USER_ID, open(), CUTOFF, projection("1200.00"));

            assertThat(result.variableSpent()).isEqualByComparingTo("380.00");
            assertThat(result.fixedSpent()).isEqualByComparingTo("1200.00");
            // cumulative by day 6: C1 300.00, C2 450.00, C3 200.00 → median 300.00 (the day-7 spikes never count)
            assertThat(result.typicalVariableByToday()).isEqualByComparingTo("300.00");
            assertThat(result.deltaVsTypical()).isEqualByComparingTo("80.00");
            // identical to the Stage 4 baseline for the same cycle and day d = 6, R = 24
            BigDecimal stage4 = historicalVariableSpendService
                    .baseline(USER_ID, DAILY_ID, PayCycle.open(USER_ID, DAILY_ID, START, END.plusDays(1)), 6, 24)
                    .typicalCumulativeAt(6);
            assertThat(result.typicalVariableByToday()).isEqualByComparingTo(stage4);
        }

        @Test
        void belowTypicalGivesANegativeDelta() {
            stubHistory();
            stubDaily(START, CUTOFF, "20.00", "0.00", "0.00", "0.00", "0.00", "5.55");

            DashboardCycleSoFarDto result = sections.cycleSoFar(daily, USER_ID, open(), CUTOFF, projection("0.00"));

            assertThat(result.variableSpent()).isEqualByComparingTo("25.55");
            assertThat(result.deltaVsTypical()).isEqualByComparingTo("-274.45");
        }

        @Test
        void variableIsThePaceEligibleSeriesNeverGrossOrUnlinkedTotals() {
            stubHistory();
            stubDaily(START, CUTOFF, "10.00", "0.00", "0.00", "0.00", "0.00", "0.00");

            sections.cycleSoFar(daily, USER_ID, open(), CUTOFF, projection("0.00"));

            verify(transactionQueryService).dailyVariableTotals(DAILY_ID, USER_ID, START, CUTOFF);
            verify(transactionQueryService, never()).sumUnlinked(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
            verify(transactionQueryService, never()).totals(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void withoutHistoryTypicalAndDeltaAreNullButSpendIsStillStated() {
            when(payCycleService.history(USER_ID, HistoricalVariableSpendService.DEFAULT_CYCLES)).thenReturn(List.of());
            stubDaily(START, CUTOFF, "10.00", "20.00", "0.00", "0.00", "0.00", "0.00");

            DashboardCycleSoFarDto result = sections.cycleSoFar(daily, USER_ID, open(), CUTOFF, projection("99.99"));

            assertThat(result.variableSpent()).isEqualByComparingTo("30.00");
            assertThat(result.fixedSpent()).isEqualByComparingTo("99.99");
            assertThat(result.typicalVariableByToday()).isNull();
            assertThat(result.deltaVsTypical()).isNull();
        }

        @Test
        void awaitingSalaryCountsThroughTheCutoffAndUsesCyclesThatReachedThatDay() {
            stubHistory();
            // expected payday Oct 9 passed; cutoff Oct 10 = day 32: no baseline cycle is 32 days long
            LocalDate cutoff = LocalDate.of(2026, 10, 10);
            PeriodDto awaiting = new PeriodDto(START, END, END.plusDays(1), PeriodType.PAY_CYCLE,
                    CycleState.AWAITING_SALARY, END.plusDays(1), true);
            when(transactionQueryService.dailyVariableTotals(DAILY_ID, USER_ID, START, cutoff))
                    .thenReturn(List.of(new DailyTotal(START, new BigDecimal("42.00"))));

            DashboardCycleSoFarDto result = sections.cycleSoFar(daily, USER_ID, awaiting, cutoff, projection("0.00"));

            assertThat(result.variableSpent()).isEqualByComparingTo("42.00");
            assertThat(result.typicalVariableByToday()).isNull();
            assertThat(result.deltaVsTypical()).isNull();
        }

        @Test
        void reportingPeriodsAndNonSalaryWalletsHaveNoCycleSoFar() {
            for (PeriodDto period : notApplicable()) {
                assertThat(sections.cycleSoFar(daily, USER_ID, period, CUTOFF, projection("1.00")))
                        .as(period.periodType() + " salary=" + period.salaryWallet())
                        .isNull();
            }
            verifyNoInteractions(transactionQueryService, payCycleService);
        }
    }

    @Nested
    class Savings {

        private final Wallet savings = Wallet.builder().id(SAVINGS_ID).name("savings").balance(new BigDecimal("460.01")).build();

        @Test
        void singleSavingsWalletCarriesItsBalanceAndTheStage62CurrentCycleNet() {
            when(walletRepository.findByUserIdAndIsFundFalse(USER_ID)).thenReturn(List.of(daily, savings));
            // 6.2 semantics from the salary wallet's side: in 500.00 from savings, out 200.00 to savings → net +300.00
            when(transferSummaryService.getSummary(DAILY_ID, USER_ID, 1)).thenReturn(summary(
                    new CounterWalletFlow(SAVINGS_ID, "savings", new BigDecimal("500.00"), new BigDecimal("200.00"),
                            new BigDecimal("300.00"))));

            DashboardSavingsDto result = sections.savings(DAILY_ID, USER_ID, open());

            assertThat(result.counterWalletName()).isEqualTo("savings");
            assertThat(result.balance()).isEqualByComparingTo("460.01");
            assertThat(result.netFlowThisCycle()).isEqualByComparingTo("300.00");
        }

        @Test
        void moneyMovedIntoSavingsIsANegativeNetFromTheSalaryWalletsSide() {
            when(walletRepository.findByUserIdAndIsFundFalse(USER_ID)).thenReturn(List.of(daily, savings));
            when(transferSummaryService.getSummary(DAILY_ID, USER_ID, 1)).thenReturn(summary(
                    new CounterWalletFlow(SAVINGS_ID, "savings", BigDecimal.ZERO, new BigDecimal("750.00"),
                            new BigDecimal("-750.00"))));

            assertThat(sections.savings(DAILY_ID, USER_ID, open()).netFlowThisCycle()).isEqualByComparingTo("-750.00");
        }

        @Test
        void noTransfersThisCycleGivesAZeroNetAndTheActualBalance() {
            when(walletRepository.findByUserIdAndIsFundFalse(USER_ID)).thenReturn(List.of(daily, savings));
            when(transferSummaryService.getSummary(DAILY_ID, USER_ID, 1)).thenReturn(summary());

            DashboardSavingsDto result = sections.savings(DAILY_ID, USER_ID, open());

            assertThat(result.balance()).isEqualByComparingTo("460.01");
            assertThat(result.netFlowThisCycle()).isEqualByComparingTo("0.00");
        }

        @Test
        void transfersWithAnotherWalletAreNotAttributedToSavings() {
            when(walletRepository.findByUserIdAndIsFundFalse(USER_ID)).thenReturn(List.of(daily, savings));
            when(transferSummaryService.getSummary(DAILY_ID, USER_ID, 1)).thenReturn(summary(
                    new CounterWalletFlow(99, "old account", new BigDecimal("40.00"), BigDecimal.ZERO,
                            new BigDecimal("40.00"))));

            assertThat(sections.savings(DAILY_ID, USER_ID, open()).netFlowThisCycle()).isEqualByComparingTo("0.00");
        }

        @Test
        void noSecondWalletMeansNoSavingsAndNoTransferRead() {
            when(walletRepository.findByUserIdAndIsFundFalse(USER_ID)).thenReturn(List.of(daily));

            assertThat(sections.savings(DAILY_ID, USER_ID, open())).isNull();
            verifyNoInteractions(transferSummaryService);
        }

        @Test
        void severalCandidateWalletsAreAmbiguousAndGiveNoSavings() {
            Wallet cash = Wallet.builder().id(3).name("cash").balance(new BigDecimal("300.00")).build();
            when(walletRepository.findByUserIdAndIsFundFalse(USER_ID)).thenReturn(List.of(daily, savings, cash));

            assertThat(sections.savings(DAILY_ID, USER_ID, open())).isNull();
            verifyNoInteractions(transferSummaryService);
        }

        @Test
        void reportingPeriodsAndNonSalaryWalletsHaveNoSavings() {
            for (PeriodDto period : notApplicable()) {
                assertThat(sections.savings(DAILY_ID, USER_ID, period)).isNull();
            }
            verifyNoInteractions(walletRepository, transferSummaryService);
        }

        private TransferSummaryResponse summary(CounterWalletFlow... flows) {
            BigDecimal in = BigDecimal.ZERO;
            BigDecimal out = BigDecimal.ZERO;
            for (CounterWalletFlow flow : flows) {
                in = in.add(flow.in());
                out = out.add(flow.out());
            }
            CycleSummary current = new CycleSummary(START, END, CycleState.OPEN, in, out, in.subtract(out), List.of(flows));
            return new TransferSummaryResponse(DAILY_ID, current, List.of(),
                    List.of(new CounterWallet(SAVINGS_ID, "savings", new BigDecimal("460.01"))));
        }
    }

    private static PeriodDto open() {
        return new PeriodDto(START, END, END.plusDays(1), PeriodType.PAY_CYCLE, CycleState.OPEN, END.plusDays(1), true);
    }

    private static List<PeriodDto> notApplicable() {
        return List.of(
                PeriodDto.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1), PeriodType.MONTHLY),
                PeriodDto.of(START, CUTOFF, CUTOFF, PeriodType.CUSTOM),
                new PeriodDto(C1.start(), C1.end(), START, PeriodType.LAST_PAY_CYCLE, CycleState.CLOSED, null, true),
                // a non-salary wallet viewing PAY_CYCLE falls back to a calendar month
                new PeriodDto(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1),
                        PeriodType.PAY_CYCLE, null, null, false));
    }

    /**
     * Three closed cycles; cumulative pace-eligible variable spend by day 6 = 300.00 / 450.00 / 200.00, each with a
     * large day-7 spend that must not reach the day-6 figure.
     */
    private void stubHistory() {
        when(payCycleService.history(USER_ID, HistoricalVariableSpendService.DEFAULT_CYCLES)).thenReturn(List.of(C1, C2, C3));
        stubCycle(C1, "300.00");
        stubCycle(C2, "450.00");
        stubCycle(C3, "200.00");
    }

    private void stubCycle(PayCycle cycle, String byDay6) {
        List<DailyTotal> series = new ArrayList<>();
        for (LocalDate day = cycle.start(); !day.isAfter(cycle.end()); day = day.plusDays(1)) {
            int index = (int) (day.toEpochDay() - cycle.start().toEpochDay()) + 1;
            String amount = index == 6 ? byDay6 : index == 7 ? "1000.00" : "0.00";
            series.add(new DailyTotal(day, new BigDecimal(amount)));
        }
        when(transactionQueryService.dailyVariableTotals(DAILY_ID, USER_ID, cycle.start(), cycle.end())).thenReturn(series);
    }

    private void stubDaily(LocalDate from, LocalDate to, String... amounts) {
        List<DailyTotal> series = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            series.add(new DailyTotal(from.plusDays(i), new BigDecimal(amounts[i])));
        }
        when(transactionQueryService.dailyVariableTotals(DAILY_ID, USER_ID, from, to)).thenReturn(series);
    }

    /** Only {@code linkedFixedExpensesToDate} is read. */
    private static SpendingProjectionDto projection(String linkedFixed) {
        return new SpendingProjectionDto(PeriodType.PAY_CYCLE, "Current pay cycle", START, END, 30, 6, 24,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO, null, null,
                BigDecimal.ZERO, new BigDecimal(linkedFixed), null, null, true, null, null);
    }
}
