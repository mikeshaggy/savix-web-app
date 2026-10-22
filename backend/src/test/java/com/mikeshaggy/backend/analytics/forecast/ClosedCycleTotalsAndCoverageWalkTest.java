package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.CommittedOccurrence;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator.OccurrenceCoverage;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.CycleVariableTotal;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceBucket;
import com.mikeshaggy.backend.regression.September2026Fixture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The two Stage 7.3 additions to the Stage 4 forecast package: closed-cycle variable totals and the public SHORT walk. */
class ClosedCycleTotalsAndCoverageWalkTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final int WALLET = 1;

    @Nested
    class ClosedCycleTotals {

        private final PayCycleService payCycleService = mock(PayCycleService.class);
        private final AnalyticsTransactionQueryService queries = mock(AnalyticsTransactionQueryService.class);
        private final HistoricalVariableSpendService service = new HistoricalVariableSpendService(payCycleService, queries);

        @Test
        void wholeCycleTotals_newestFirst_fromOneQuery() {
            PayCycle august = PayCycle.closed(USER, WALLET, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 8));
            PayCycle july = PayCycle.closed(USER, WALLET, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 9));
            when(payCycleService.history(USER, 7)).thenReturn(List.of(august, july));
            List<DailyTotal> daily = new ArrayList<>();
            for (LocalDate day = july.start(); !day.isAfter(august.end()); day = day.plusDays(1)) {
                daily.add(new DailyTotal(day, august.contains(day) ? new BigDecimal("120.02") : new BigDecimal("10.00")));
            }
            when(queries.dailyVariableTotals(WALLET, USER, july.start(), august.end())).thenReturn(daily);

            List<CycleVariableTotal> totals = service.closedCycleTotals(USER, WALLET, 7);

            assertThat(totals).extracting(CycleVariableTotal::cycle).containsExactly(august, july);
            assertThat(totals.get(0).variableTotal()).isEqualByComparingTo("3600.60"); // 30 × 120.02
            assertThat(totals.get(1).variableTotal()).isEqualByComparingTo("310.00");  // 31 × 10.00
            verify(queries, times(1)).dailyVariableTotals(any(), any(), any(), any());
        }

        @Test
        void cappedAtSeven_andEmptyWithoutHistory() {
            when(payCycleService.history(USER, 7)).thenReturn(List.of());

            assertThat(service.closedCycleTotals(USER, WALLET, 50)).isEmpty();
            verify(payCycleService).history(USER, 7);
            verifyNoInteractions(queries);
        }
    }

    @Nested
    class CoverageWalk {

        private static final LocalDate TODAY = September2026Fixture.TODAY;
        private static final LocalDate CYCLE_END = LocalDate.of(2026, 10, 8);

        @Test
        void auditedFixture_everyStepCoverable_andAvailableMatchesTheHandComputedWalk() {
            List<OccurrenceCoverage> walk = ForecastV2Calculator.coverageWalk(pending(),
                    September2026Fixture.SALARY_WALLET_BALANCE, new BigDecimal("4677.20"), TODAY, 24);

            assertThat(walk).allMatch(OccurrenceCoverage::coverable);
            assertThat(walk).extracting(step -> step.availableBefore().toPlainString())
                    .containsExactly("4727.59", "3676.19", "2401.89", "1258.48", "833.72");
        }

        @Test
        void agreesWithTheForecastStatus() {
            ForecastV2Calculator calculator = new ForecastV2Calculator();
            for (String balance : List.of("5896.89", "4500.00", "1200.00", "794.27")) {
                var pace = calculator.currentPace(September2026Fixture.DAILY_VARIABLE_SEP_9_TO_14.stream()
                        .map(amount -> new DailyTotal(TODAY, amount)).toList(), 24);
                var baseline = new HistoricalVariableSpendService.HistoricalBaseline(3, new BigDecimal("2880.48"),
                        new BigDecimal("2400.00"), new BigDecimal("3400.00"), List.of(), new BigDecimal("120.02"));
                var forecast = calculator.forecast(new ForecastV2Calculator.ForecastInput(new BigDecimal(balance), TODAY,
                        6, 30, 24, pending(), pace, baseline));

                boolean walkCoverable = ForecastV2Calculator.coverageWalk(pending(), new BigDecimal(balance),
                        forecast.pessimisticVariableRemaining(), TODAY, 24).stream()
                        .allMatch(OccurrenceCoverage::coverable);
                // a negative discretionaryNow can never pass the walk, so SHORT ⇔ some step is uncoverable
                assertThat(walkCoverable).as("balance %s", balance).isEqualTo(forecast.status() != ForecastStatus.SHORT);
            }
        }

        private static List<CommittedOccurrence> pending() {
            return September2026Fixture.PENDING_OCCURRENCES.stream()
                    .map(o -> new CommittedOccurrence(o.dueDate(), o.plannedAmount(),
                            FixedOccurrenceBucket.of(OccurrenceStatus.PENDING, o.dueDate(), TODAY, CYCLE_END)))
                    .toList();
        }
    }
}
