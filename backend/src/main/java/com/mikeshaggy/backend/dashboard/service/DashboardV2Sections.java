package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService;
import com.mikeshaggy.backend.analytics.forecast.HistoricalVariableSpendService.HistoricalBaseline;
import com.mikeshaggy.backend.analytics.forecast.ForecastTrajectoryDto.BandPoint;
import com.mikeshaggy.backend.analytics.forecast.ForecastV2Dto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService.DailyTotal;
import com.mikeshaggy.backend.common.calculation.MoneyMath;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.dashboard.dto.DashboardCycleSoFarDto;
import com.mikeshaggy.backend.dashboard.dto.DashboardSavingsDto;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse;
import com.mikeshaggy.backend.transfer.dto.TransferSummaryResponse.CounterWalletFlow;
import com.mikeshaggy.backend.transfer.service.TransferSummaryService;
import com.mikeshaggy.backend.wallet.domain.SavingsWallets;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.zero;

/**
 * Stage 6.3 — the {@code dashboard-v2} sections of the dashboard summary. Both read existing sources only:
 * {@code cycleSoFar} the Stage 4 pace-eligible series and historical baseline plus the projection's ledger-linked
 * fixed spend, {@code savings} the Stage 6.2 transfer summary and the savings wallet itself. Flags are not read
 * here; {@link DashboardSummaryService} calls this only with {@code dashboard-v2} on.
 *
 * <p>Both apply to the salary wallet's {@code PAY_CYCLE} (open or awaiting its salary) only; every reporting period
 * and every other wallet gets {@code null}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardV2Sections {

    /** One closed cycle is enough to read the 6.2 current cycle; the dashboard never shows the closed ones. */
    private static final int TRANSFER_SUMMARY_CYCLES = 1;

    private final AnalyticsTransactionQueryService transactionQueryService;
    private final HistoricalVariableSpendService historicalVariableSpendService;
    private final TransferSummaryService transferSummaryService;
    private final WalletRepository walletRepository;

    /** The open/awaiting half of {@code ForecastService#isApplicable}: same period, wallet and state rule. */
    public static boolean applies(PeriodDto period) {
        return period.periodType() == PeriodType.PAY_CYCLE
                && Boolean.TRUE.equals(period.salaryWallet())
                && (period.cycleState() == CycleState.OPEN || period.cycleState() == CycleState.AWAITING_SALARY);
    }

    /**
     * @param cutoffDate the dashboard's data horizon for {@code period} (day {@code d} = days from its start)
     * @param projection the dashboard's projection for the same period and cutoff (source of {@code fixedSpent})
     */
    public DashboardCycleSoFarDto cycleSoFar(Wallet wallet, UUID userId, PeriodDto period, LocalDate cutoffDate,
                                             SpendingProjectionDto projection) {
        if (!applies(period)) {
            return null;
        }
        LocalDate start = period.startDate();
        // the pace-eligible series of the Stage 4 forecast, over the same days the dashboard counts
        List<DailyTotal> daily = transactionQueryService.dailyVariableTotals(wallet.getId(), userId, start, cutoffDate);
        BigDecimal variableSpent = money(daily.stream()
                .map(DailyTotal::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal fixedSpent = money(projection.linkedFixedExpensesToDate());

        int dayIndex = InclusiveDateRange.daysBetween(start, cutoffDate);
        BigDecimal typical = typicalByToday(wallet, userId, period, cutoffDate, dayIndex, projection);
        BigDecimal delta = typical == null ? null : money(variableSpent.subtract(typical));

        return new DashboardCycleSoFarDto(variableSpent, fixedSpent, typical, delta);
    }

    /**
     * The baseline's typical cumulative variable spend at day {@code dayIndex}. With Forecast v2 on for an OPEN cycle
     * the projection already carries the trajectory whose {@code typical} median at that day is the same figure
     * ({@code ForecastTrajectoryBuilder}: same baseline cycles, same {@code Quantiles.median}; ForecastService caps
     * its day at the period end exactly as the dashboard caps its cutoff), so it is read from there and the baseline
     * is not computed a second time; no point at that day means no baseline cycle reached it ({@code null}).
     * Without a trajectory (forecast-v2 off, awaiting salary) the baseline is computed here.
     */
    private BigDecimal typicalByToday(Wallet wallet, UUID userId, PeriodDto period, LocalDate cutoffDate,
                                      int dayIndex, SpendingProjectionDto projection) {
        ForecastV2Dto forecast = projection.forecast();
        if (forecast != null && forecast.trajectory() != null) {
            return forecast.trajectory().typical().stream()
                    .filter(point -> point.dayIndex() == dayIndex)
                    .map(BandPoint::median)
                    .findFirst()
                    .orElse(null);
        }
        int daysRemaining = Math.max(0, InclusiveDateRange.daysBetween(cutoffDate.plusDays(1), period.endDate()));
        // only the start is read by the baseline (closed cycles ending before it); same shape as ForecastService
        PayCycle currentCycle = PayCycle.open(userId, wallet.getId(), period.startDate(), period.endDate().plusDays(1));
        HistoricalBaseline baseline = historicalVariableSpendService.baseline(
                userId, wallet.getId(), currentCycle, dayIndex, daysRemaining);
        return baseline.typicalCumulativeAt(dayIndex);
    }

    /**
     * The savings wallet is the single non-fund wallet besides the salary wallet; with none or several there is no
     * unambiguous savings wallet and the section is {@code null}. Its net flow is the Stage 6.2 current-cycle row for
     * that counter-wallet ({@code 0.00} when it has no transfers this cycle).
     */
    public DashboardSavingsDto savings(Integer walletId, UUID userId, PeriodDto period) {
        if (!applies(period)) {
            return null;
        }
        Optional<Wallet> single = SavingsWallets.single(walletRepository.findByUserIdAndIsFundFalse(userId), walletId);
        if (single.isEmpty()) {
            return null;
        }
        Wallet savings = single.get();

        TransferSummaryResponse summary = transferSummaryService.getSummary(walletId, userId, TRANSFER_SUMMARY_CYCLES);
        BigDecimal net = summary.currentCycle() == null
                ? zero()
                : summary.currentCycle().counterWallets().stream()
                        .filter(flow -> savings.getId().equals(flow.walletId()))
                        .map(CounterWalletFlow::net)
                        .findFirst()
                        .map(MoneyMath::money)
                        .orElse(zero());
        return new DashboardSavingsDto(savings.getName(), money(savings.getBalance()), net);
    }
}
