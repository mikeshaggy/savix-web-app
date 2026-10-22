package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.forecast.ForecastService;
import com.mikeshaggy.backend.analytics.forecast.ForecastTrajectoryDto;
import com.mikeshaggy.backend.analytics.forecast.ForecastTrajectoryDto.BandPoint;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Calculator;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService;
import com.mikeshaggy.backend.analytics.forecast.OneOffDetector;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.dashboard.dto.DashboardCycleSoFarDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedProgressDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedSummaryDto;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.dto.RiskIndicatorDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.transfer.service.TransferSummaryService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stage 6 de-dup: with Forecast v2 on, {@code cycleSoFar.typicalVariableByToday} is read from the projection's
 * trajectory instead of computing the historical baseline a second time — and must equal the baseline path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardV2SectionsBaselineOnceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Integer WALLET_ID = 1;
    private static final LocalDate START = LocalDate.of(2026, 9, 9);
    private static final LocalDate END = LocalDate.of(2026, 10, 8);

    /** Baseline cycles of 20, 20 and 21 days: the current 30-day cycle outlasts all of them from day 22 on. */
    private static final PayCycle C1 = PayCycle.closed(USER_ID, WALLET_ID, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 8));
    private static final PayCycle C2 = PayCycle.closed(USER_ID, WALLET_ID, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 19));
    private static final PayCycle C3 = PayCycle.closed(USER_ID, WALLET_ID, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 30));

    @Mock private AnalyticsTransactionQueryService transactionQueryService;
    @Mock private PayCycleService payCycleService;
    @Mock private FixedPaymentDashboardService fixedPaymentDashboardService;

    private final Wallet wallet = Wallet.builder().id(WALLET_ID).name("daily").balance(new BigDecimal("5000.00")).build();
    private HistoricalVariableSpendService historicalVariableSpendService;

    @BeforeEach
    void setUp() {
        historicalVariableSpendService = new HistoricalVariableSpendService(payCycleService, transactionQueryService);
        when(payCycleService.history(USER_ID, HistoricalVariableSpendService.DEFAULT_CYCLES))
                .thenReturn(List.of(C1, C2, C3));
        stubCycle(C1, 3);
        stubCycle(C2, 5);
        stubCycle(C3, 8);
    }

    @Test
    void trajectoryPathEqualsTheBaselinePathAtEveryCutoffIncludingBeyondAllBaselineCycles() {
        DashboardV2Sections baselineSections = sections(historicalVariableSpendService);
        ForecastService forecastService = new ForecastService(transactionQueryService, fixedPaymentDashboardService,
                historicalVariableSpendService, new ForecastV2Calculator(), new OneOffDetector(new ForecastV2Calculator()),
                Clock.systemUTC());
        when(fixedPaymentDashboardService.getFixedPaymentsTileData(any(), any(Wallet.class), any(), any())).thenReturn(emptyTile());

        // day 1, 6, 20 (every baseline cycle reaches it), 21 (one does), 25 and 30 (none does)
        for (int day : new int[]{1, 6, 20, 21, 25, 30}) {
            LocalDate cutoff = START.plusDays(day - 1);
            List<DailyTotal> current = new ArrayList<>();
            for (int i = 0; i < day; i++) {
                current.add(new DailyTotal(START.plusDays(i), new BigDecimal("12.34")));
            }
            when(transactionQueryService.dailyVariableTotals(WALLET_ID, USER_ID, START, cutoff)).thenReturn(current);

            ForecastV2Dto forecast = forecastService.forecast(wallet, USER_ID, open(), cutoff, null);
            SpendingProjectionDto withForecast = projection().withForecast(forecast);

            DashboardCycleSoFarDto viaTrajectory = sections(mock(HistoricalVariableSpendService.class))
                    .cycleSoFar(wallet, USER_ID, open(), cutoff, withForecast);
            DashboardCycleSoFarDto viaBaseline = baselineSections.cycleSoFar(wallet, USER_ID, open(), cutoff, projection());

            assertThat(viaTrajectory.typicalVariableByToday()).as("day " + day).isEqualTo(viaBaseline.typicalVariableByToday());
            assertThat(viaTrajectory.deltaVsTypical()).as("day " + day).isEqualTo(viaBaseline.deltaVsTypical());
            assertThat(viaTrajectory.variableSpent()).isEqualTo(viaBaseline.variableSpent());
            if (day >= 25) {
                assertThat(viaBaseline.typicalVariableByToday()).as("beyond all baseline cycles").isNull();
            } else {
                assertThat(viaBaseline.typicalVariableByToday()).as("day " + day).isNotNull();
            }
        }
    }

    @Test
    void theBaselineIsNeverComputedWhenTheProjectionCarriesTheTrajectory() {
        HistoricalVariableSpendService baseline = mock(HistoricalVariableSpendService.class);
        stubDailyOnly(START.plusDays(5));
        ForecastTrajectoryDto trajectory = new ForecastTrajectoryDto(List.of(), List.of(
                new BandPoint(5, START.plusDays(4), BigDecimal.ONE, new BigDecimal("111.00"), BigDecimal.TEN, 3),
                new BandPoint(6, START.plusDays(5), BigDecimal.ONE, new BigDecimal("222.00"), BigDecimal.TEN, 3)));

        DashboardCycleSoFarDto result = sections(baseline).cycleSoFar(wallet, USER_ID, open(), START.plusDays(5),
                projection().withForecast(forecastWith(trajectory)));

        assertThat(result.typicalVariableByToday()).isEqualByComparingTo("222.00");
        verify(baseline, never()).baseline(any(), anyInt(), any(), anyInt(), anyInt());
    }

    @Test
    void aTrajectoryWithoutAPointAtTodayMeansNoTypicalAndStillNoBaselineCall() {
        HistoricalVariableSpendService baseline = mock(HistoricalVariableSpendService.class);
        stubDailyOnly(START.plusDays(5));
        ForecastTrajectoryDto trajectory = new ForecastTrajectoryDto(List.of(), List.of(
                new BandPoint(5, START.plusDays(4), BigDecimal.ONE, new BigDecimal("111.00"), BigDecimal.TEN, 3)));

        DashboardCycleSoFarDto result = sections(baseline).cycleSoFar(wallet, USER_ID, open(), START.plusDays(5),
                projection().withForecast(forecastWith(trajectory)));

        assertThat(result.typicalVariableByToday()).isNull();
        assertThat(result.deltaVsTypical()).isNull();
        verify(baseline, never()).baseline(any(), anyInt(), any(), anyInt(), anyInt());
    }

    @Test
    void withoutATrajectoryTheBaselineIsComputedOnce() {
        HistoricalVariableSpendService baseline = mock(HistoricalVariableSpendService.class);
        HistoricalVariableSpendService.HistoricalBaseline real = historicalVariableSpendService.baseline(
                USER_ID, WALLET_ID, PayCycle.open(USER_ID, WALLET_ID, START, END.plusDays(1)), 6, 24);
        when(baseline.baseline(any(), anyInt(), any(), anyInt(), anyInt())).thenReturn(real);
        stubDailyOnly(START.plusDays(5));

        sections(baseline).cycleSoFar(wallet, USER_ID, open(), START.plusDays(5), projection());

        verify(baseline).baseline(any(), anyInt(), any(), anyInt(), anyInt());
    }

    private DashboardV2Sections sections(HistoricalVariableSpendService baseline) {
        return new DashboardV2Sections(transactionQueryService, baseline,
                mock(TransferSummaryService.class), mock(WalletRepository.class));
    }

    private void stubDailyOnly(LocalDate cutoff) {
        when(transactionQueryService.dailyVariableTotals(WALLET_ID, USER_ID, START, cutoff))
                .thenReturn(List.of(new DailyTotal(START, new BigDecimal("10.00"))));
    }

    /** Day {@code spikeDay} carries 100.00 and every other day 1.00, so cumulative values differ per cycle and day. */
    private void stubCycle(PayCycle cycle, int spikeDay) {
        List<DailyTotal> series = new ArrayList<>();
        int index = 1;
        for (LocalDate day = cycle.start(); !day.isAfter(cycle.end()); day = day.plusDays(1), index++) {
            series.add(new DailyTotal(day, new BigDecimal(index == spikeDay ? "100.00" : "1.00")));
        }
        when(transactionQueryService.dailyVariableTotals(WALLET_ID, USER_ID, cycle.start(), cycle.end())).thenReturn(series);
    }

    private static ForecastV2Dto forecastWith(ForecastTrajectoryDto trajectory) {
        return new ForecastV2Dto(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, List.of(), List.of(), List.of(), null, null, null, null, trajectory);
    }

    private static PeriodDto open() {
        return new PeriodDto(START, END, END.plusDays(1), PeriodType.PAY_CYCLE, CycleState.OPEN, END.plusDays(1), true);
    }

    private static SpendingProjectionDto projection() {
        return new SpendingProjectionDto(PeriodType.PAY_CYCLE, "Current pay cycle", START, END, 30, 6, 24,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, BigDecimal.ZERO, null, null,
                BigDecimal.ZERO, new BigDecimal("50.00"), null, null, true, null, null);
    }

    private static FixedTransactionsTileDto emptyTile() {
        FixedSummaryDto summary = new FixedSummaryDto(BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        FixedProgressDto progress = new FixedProgressDto(0, 0, BigDecimal.ZERO, null, null, null, null, 0);
        return new FixedTransactionsTileDto(START, END, END.plusDays(1), END.plusDays(1), CycleState.OPEN, summary,
                progress, new BigDecimal("5000.00"), new BigDecimal("5000.00"), new RiskIndicatorDto(false, null),
                List.of(), List.of(), List.of(), null, List.of());
    }
}
