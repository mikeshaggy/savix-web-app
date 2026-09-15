package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.query.AnalyticsTransactionQueryService;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.PeriodService;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpendingProjectionService {

    private final AnalyticsTransactionQueryService transactionQueryService;
    private final WalletService walletService;
    private final PeriodService periodService;
    private final FixedPaymentDashboardService fixedPaymentDashboardService;
    private final SpendingProjectionCalculator projectionCalculator;
    private final Clock clock;

    public SpendingProjectionDto getSpendingProjection(Integer walletId, UUID userId,
                                                       PeriodType periodType,
                                                       LocalDate startDate, LocalDate endDate) {
        return getSpendingProjection(walletId, userId, periodType, startDate, endDate, null);
    }

    public SpendingProjectionDto getSpendingProjection(Integer walletId, UUID userId,
                                                       PeriodType periodType,
                                                       LocalDate startDate, LocalDate endDate,
                                                       LocalDate asOfDate) {
        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        PeriodDto resolved = periodService.resolve(periodType, walletId, userId, startDate, endDate);
        return getSpendingProjection(wallet, userId, resolved, asOfDate);
    }

    public SpendingProjectionDto getSpendingProjection(Wallet wallet, UUID userId,
                                                       PeriodDto resolvedPeriod, LocalDate asOfDate) {
        return getSpendingProjection(wallet, userId, resolvedPeriod, asOfDate, null);
    }

    public SpendingProjectionDto getSpendingProjection(Wallet wallet, UUID userId,
                                                       PeriodDto resolvedPeriod, LocalDate asOfDate,
                                                       BigDecimal precomputedRemainingFixed) {
        PeriodWindow period = resolveProjectionWindow(resolvedPeriod);

        LocalDate today = asOfDate == null ? LocalDate.now(clock) : asOfDate;
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
                : remainingFixedPayments(resolvedPeriod, period, wallet, userId, today);
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
                projectionCalculator.projectionReason(resolvedPeriod.periodType(), resolvedPeriod.cycleState()));
    }

    /** The projection window is the resolved period itself; pay-cycle-v2 already ends a PAY_CYCLE the day before the next payday. */
    private PeriodWindow resolveProjectionWindow(PeriodDto period) {
        return new PeriodWindow(period.startDate(), period.endDate());
    }

    private BigDecimal remainingFixedPayments(PeriodDto resolved, PeriodWindow period,
                                             Integer walletId, UUID userId, LocalDate asOfDate) {
        PeriodDto fixedPaymentPeriod = PeriodDto.of(
                period.startDate(),
                period.endDate(),
                period.endDate(),
                resolved.periodType());
        FixedTransactionsTileDto tile = fixedPaymentDashboardService
                .getFixedPaymentsTileData(fixedPaymentPeriod, walletId, userId, asOfDate);
        return money(tile.summary().remainingAmount());
    }

    private BigDecimal remainingFixedPayments(PeriodDto resolved, PeriodWindow period,
                                             Wallet wallet, UUID userId, LocalDate asOfDate) {
        PeriodDto fixedPaymentPeriod = PeriodDto.of(
                period.startDate(),
                period.endDate(),
                period.endDate(),
                resolved.periodType());
        FixedTransactionsTileDto tile = fixedPaymentDashboardService
                .getFixedPaymentsTileData(fixedPaymentPeriod, wallet, userId, asOfDate);
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
