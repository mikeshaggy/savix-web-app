package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator.PeriodWindow;
import static com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator.ProjectionInput;
import static com.mikeshaggy.backend.analytics.forecast.SpendingProjectionCalculator.ProjectionResult;

/**
 * Legacy spending projection (linear extrapolation) plus, since Stage 4.7, the Forecast v2 policy for the two
 * consumer paths — the {@code /analytics/projections} endpoint and the dashboard summary:
 * <ul>
 *   <li>{@code forecast-v2} on → {@link SpendingProjectionDto#forecast()} is populated by {@link ForecastService}
 *       when the period is applicable; the legacy fields stay populated for the comparison window.</li>
 *   <li>{@code forecast-v2-shadow} on and {@code forecast-v2} off → v2 is computed server-side and compared in
 *       one log line by {@link ForecastShadowObserver}; the payload is the legacy one, {@code forecast == null}.</li>
 *   <li>both off → the legacy projection only; v2 is never computed.</li>
 * </ul>
 * {@link #getSpendingProjection(Wallet, UUID, PeriodDto, LocalDate)} is the legacy-only entry used by internal
 * readers (insights, the analytics overview): it neither computes nor logs v2.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpendingProjectionService {

    private final AnalyticsTransactionQueryService transactionQueryService;
    private final WalletService walletService;
    private final PeriodService periodService;
    private final FixedPaymentDashboardService fixedPaymentDashboardService;
    private final SpendingProjectionCalculator projectionCalculator;
    private final ForecastService forecastService;
    private final ForecastShadowObserver forecastShadowObserver;
    private final FeatureFlags featureFlags;
    private final Clock clock;

    /** The {@code /analytics/projections} endpoint path. */
    public SpendingProjectionDto getSpendingProjection(Integer walletId, UUID userId,
                                                       PeriodType periodType,
                                                       LocalDate startDate, LocalDate endDate) {
        return getSpendingProjection(walletId, userId, periodType, startDate, endDate, null);
    }

    /**
     * The {@code /analytics/projections} endpoint path: legacy projection, then the Forecast v2 policy. The Stage 3
     * tile is loaded at most once and shared by the legacy {@code remainingFixedPayments} and the v2 committed set;
     * in shadow mode this is the one place that emits the comparison line for this request.
     */
    public SpendingProjectionDto getSpendingProjection(Integer walletId, UUID userId,
                                                       PeriodType periodType,
                                                       LocalDate startDate, LocalDate endDate,
                                                       LocalDate asOfDate) {
        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        PeriodDto resolved = periodService.resolve(periodType, walletId, userId, startDate, endDate);
        LocalDate today = asOfDate == null ? LocalDate.now(clock) : asOfDate;

        // exactly what the legacy path loaded before Stage 4.7: the tile, only for a projectable period
        FixedTransactionsTileDto tile = projectionCalculator.isProjectionAvailable(
                resolved.periodType(), resolved.cycleState())
                ? fixedPaymentDashboardService.getFixedPaymentsTileData(resolved, wallet, userId, today)
                : null;
        SpendingProjectionDto legacy = legacyProjection(wallet, userId, resolved, today,
                tile == null ? null : tile.summary().remainingAmount());
        SpendingProjectionDto response = withForecast(legacy, wallet, userId, resolved, today, tile);
        forecastShadowObserver.observe(wallet, userId, resolved, today, tile, legacy,
                ForecastShadowObserver.legacyVerdict(legacy));
        return response;
    }

    /**
     * Legacy-only projection for internal readers (insights, analytics overview): Forecast v2 is neither computed
     * nor shadow-logged here, so those readers never double a consumer request's work or its log line.
     */
    public SpendingProjectionDto getSpendingProjection(Wallet wallet, UUID userId,
                                                       PeriodDto resolvedPeriod, LocalDate asOfDate) {
        LocalDate today = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        return legacyProjection(wallet, userId, resolvedPeriod, today, null);
    }

    /**
     * The dashboard path: the caller already holds the Stage 3 tile for {@code resolvedPeriod} / {@code asOfDate}
     * (or {@code null} where the dashboard shows none) and shares it with both projections. The dashboard emits the
     * shadow line itself, after deriving its legacy verdict, so this method never logs.
     */
    public SpendingProjectionDto getSpendingProjection(Wallet wallet, UUID userId,
                                                       PeriodDto resolvedPeriod, LocalDate asOfDate,
                                                       FixedTransactionsTileDto tile) {
        LocalDate today = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        SpendingProjectionDto legacy = legacyProjection(wallet, userId, resolvedPeriod, today,
                tile == null ? null : tile.summary().remainingAmount());
        return withForecast(legacy, wallet, userId, resolvedPeriod, today, tile);
    }

    /** Active v2: populate {@code forecast} when the flag is on; failures on this path propagate like any other. */
    private SpendingProjectionDto withForecast(SpendingProjectionDto legacy, Wallet wallet, UUID userId,
                                               PeriodDto resolvedPeriod, LocalDate today,
                                               FixedTransactionsTileDto tile) {
        if (!featureFlags.forecastV2()) {
            return legacy;
        }
        ForecastV2Dto forecast = forecastService.forecast(wallet, userId, resolvedPeriod, today, tile);
        return forecast == null ? legacy : legacy.withForecast(forecast);
    }

    /**
     * The legacy projection, unchanged since Stage 2/3: {@code precomputedRemainingFixed} is the tile's
     * {@code remainingAmount} when the caller already has it, otherwise the tile is loaded here for a projectable
     * period. {@code forecast} is always {@code null} on this level.
     */
    private SpendingProjectionDto legacyProjection(Wallet wallet, UUID userId,
                                                   PeriodDto resolvedPeriod, LocalDate today,
                                                   BigDecimal precomputedRemainingFixed) {
        PeriodWindow period = resolveProjectionWindow(resolvedPeriod);

        if (period.startDate().isAfter(today)) {
            throw new IllegalArgumentException("period must not be in the future");
        }

        // "To date" horizon: today, capped at the period end — except for a cycle awaiting its salary, which keeps
        // counting the days after the expected payday (same rule as the dashboard cutoff).
        LocalDate toDate = today.isBefore(period.endDate())
                || resolvedPeriod.cycleState() == CycleState.AWAITING_SALARY ? today : period.endDate();
        BigDecimal incomeToDate = transactionQueryService.sum(
                wallet.getId(), userId, period.startDate(), toDate, CategoryType.INCOME);
        BigDecimal incomeForPeriod = transactionQueryService.sum(
                wallet.getId(), userId, period.startDate(), period.endDate(), CategoryType.INCOME);
        BigDecimal expensesToDate = transactionQueryService.sum(
                wallet.getId(), userId, period.startDate(), toDate, CategoryType.EXPENSE);
        BigDecimal variableExpensesToDate = transactionQueryService.sumUnlinked(
                wallet.getId(), userId, period.startDate(), toDate, CategoryType.EXPENSE);

        if (!projectionCalculator.isProjectionAvailable(resolvedPeriod.periodType(), resolvedPeriod.cycleState())) {
            return reportingOnly(resolvedPeriod, period, toDate,
                    incomeToDate, incomeForPeriod, expensesToDate, variableExpensesToDate);
        }

        BigDecimal remainingFixed = precomputedRemainingFixed != null
                ? money(precomputedRemainingFixed)
                : remainingFixedPayments(resolvedPeriod, wallet, userId, today);
        ProjectionResult projection = projectionCalculator.calculate(new ProjectionInput(
                period,
                today,
                wallet.getBalance(),
                incomeForPeriod,
                expensesToDate,
                variableExpensesToDate,
                remainingFixed));

        return new SpendingProjectionDto(
                resolvedPeriod.periodType(),
                periodLabel(resolvedPeriod.periodType()),
                period.startDate(),
                period.endDate(),
                projection.daysInPeriod(),
                projection.daysElapsed(),
                projection.daysRemaining(),
                incomeToDate,
                incomeForPeriod,
                expensesToDate,
                projection.dailyBurnRate(),
                projection.projectedPeriodExpenses(),
                projection.projectedEndBalance(),
                projection.remainingFixedPayments(),
                projection.safeToSpendToday(),
                projection.safeToSpendPerDay(),
                projection.variableExpensesToDate(),
                projection.linkedFixedExpensesToDate(),
                projection.variableDailyBurnRate(),
                projection.projectedVariableRemaining(),
                true,
                null,
                null);
    }

    /**
     * Reporting periods (MONTHLY, CUSTOM, LAST_PAY_CYCLE, a cycle awaiting its salary or without a resolved state)
     * carry actuals only: every projected / safe-to-spend figure is {@code null} so no consumer can mistake a
     * to-date total for a forecast.
     */
    private SpendingProjectionDto reportingOnly(PeriodDto resolvedPeriod, PeriodWindow period, LocalDate toDate,
                                                BigDecimal incomeToDate, BigDecimal incomeForPeriod,
                                                BigDecimal expensesToDate, BigDecimal variableExpensesToDate) {
        int daysInPeriod = InclusiveDateRange.daysBetween(period.startDate(), period.endDate());
        int daysElapsed = InclusiveDateRange.daysBetween(period.startDate(), toDate);
        int daysRemaining = Math.max(0, InclusiveDateRange.daysBetween(toDate.plusDays(1), period.endDate()));
        BigDecimal variableDailyBurnRate = projectionCalculator.variableDailyBurnRate(variableExpensesToDate, daysElapsed);

        return new SpendingProjectionDto(
                resolvedPeriod.periodType(),
                periodLabel(resolvedPeriod.periodType()),
                period.startDate(),
                period.endDate(),
                daysInPeriod,
                daysElapsed,
                daysRemaining,
                incomeToDate,
                incomeForPeriod,
                expensesToDate,
                null,
                null,
                null,
                money(BigDecimal.ZERO),
                null,
                null,
                money(variableExpensesToDate),
                money(expensesToDate.subtract(variableExpensesToDate)),
                variableDailyBurnRate,
                null,
                false,
                projectionCalculator.projectionReason(resolvedPeriod.periodType(), resolvedPeriod.cycleState()),
                null);
    }

    /** The projection window is the resolved period itself; pay-cycle-v2 already ends a PAY_CYCLE the day before the next payday. */
    private PeriodWindow resolveProjectionWindow(PeriodDto period) {
        return new PeriodWindow(period.startDate(), period.endDate());
    }

    /** The committed window is the resolved period itself; the fixed-payment service derives the window from it. */
    private BigDecimal remainingFixedPayments(PeriodDto resolved, Wallet wallet, UUID userId, LocalDate asOfDate) {
        FixedTransactionsTileDto tile = fixedPaymentDashboardService
                .getFixedPaymentsTileData(resolved, wallet, userId, asOfDate);
        return money(tile.summary().remainingAmount());
    }

    private String periodLabel(PeriodType periodType) {
        return switch (periodType) {
            case PAY_CYCLE -> "Current pay cycle";
            case LAST_PAY_CYCLE -> "Last pay cycle";
            case MONTHLY -> "Current month";
            case CUSTOM -> "Custom range";
        };
    }

}
