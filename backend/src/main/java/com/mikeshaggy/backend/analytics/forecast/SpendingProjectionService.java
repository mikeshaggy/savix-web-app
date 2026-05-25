package com.mikeshaggy.backend.analytics.forecast;

import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpendingProjectionService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PeriodService periodService;
    private final FixedPaymentDashboardService fixedPaymentDashboardService;
    private final Clock clock;

    public SpendingProjectionDto getSpendingProjection(Integer walletId, UUID userId,
                                                       PeriodType periodType,
                                                       LocalDate startDate, LocalDate endDate) {
        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        PeriodDto resolved = periodService.resolve(periodType, walletId, userId, startDate, endDate);
        PeriodWindow period = resolveProjectionWindow(resolved);

        LocalDate today = LocalDate.now(clock);
        if (period.startDate().isAfter(today)) {
            throw new IllegalArgumentException("period must not be in the future");
        }

        int daysInPeriod = inclusiveDays(period.startDate(), period.endDate());
        boolean historicalPeriod = period.endDate().isBefore(today);
        boolean projectionAvailable = !historicalPeriod;
        int daysElapsed = historicalPeriod
                ? daysInPeriod
                : inclusiveDays(period.startDate(), today);
        int daysRemaining = historicalPeriod
                ? 0
                : Math.max(0, inclusiveDays(today.plusDays(1), period.endDate()));

        LocalDate toDate = today.isBefore(period.endDate()) ? today : period.endDate();
        BigDecimal incomeToDate = sum(walletId, period.startDate(), toDate, CategoryType.INCOME);
        BigDecimal incomeForPeriod = sum(walletId, period.startDate(), period.endDate(), CategoryType.INCOME);
        BigDecimal expensesToDate = sum(walletId, period.startDate(), toDate, CategoryType.EXPENSE);
        BigDecimal dailyBurnRate = daysElapsed == 0
                ? money(BigDecimal.ZERO)
                : expensesToDate.divide(BigDecimal.valueOf(daysElapsed), SCALE, ROUNDING);

        BigDecimal projectedPeriodExpenses;
        BigDecimal projectedEndBalance;
        BigDecimal remainingFixedPayments;
        BigDecimal safeToSpendToday;
        String projectionReason;

        if (projectionAvailable) {
            projectedPeriodExpenses = money(dailyBurnRate.multiply(BigDecimal.valueOf(daysInPeriod)));
            projectedEndBalance = money(incomeForPeriod.subtract(projectedPeriodExpenses));
            remainingFixedPayments = remainingFixedPayments(resolved, period, walletId, userId);
            BigDecimal projectedRemainingVariableSpend = dailyBurnRate.multiply(BigDecimal.valueOf(daysRemaining));
            safeToSpendToday = money(wallet.getBalance()
                    .subtract(remainingFixedPayments)
                    .subtract(projectedRemainingVariableSpend));
            projectionReason = null;
        } else {
            projectedPeriodExpenses = expensesToDate;
            projectedEndBalance = money(incomeForPeriod.subtract(expensesToDate));
            remainingFixedPayments = money(BigDecimal.ZERO);
            safeToSpendToday = money(BigDecimal.ZERO);
            projectionReason = "Historical period";
        }

        return new SpendingProjectionDto(
                resolved.periodType(),
                periodLabel(resolved.periodType()),
                period.startDate(),
                period.endDate(),
                daysInPeriod,
                daysElapsed,
                daysRemaining,
                incomeToDate,
                incomeForPeriod,
                expensesToDate,
                dailyBurnRate,
                projectedPeriodExpenses,
                projectedEndBalance,
                remainingFixedPayments,
                safeToSpendToday,
                projectionAvailable,
                projectionReason);
    }

    private PeriodWindow resolveProjectionWindow(PeriodDto period) {
        LocalDate endDate = switch (period.periodType()) {
            case PAY_CYCLE -> period.billingEndDate().minusDays(1);
            case LAST_PAY_CYCLE, MONTHLY, CUSTOM -> period.endDate();
        };
        return new PeriodWindow(period.startDate(), endDate);
    }

    private BigDecimal remainingFixedPayments(PeriodDto resolved, PeriodWindow period,
                                             Integer walletId, UUID userId) {
        PeriodDto fixedPaymentPeriod = new PeriodDto(
                period.startDate(),
                period.endDate(),
                period.endDate(),
                resolved.periodType());
        FixedTransactionsTileDto tile = fixedPaymentDashboardService
                .getFixedPaymentsTileData(fixedPaymentPeriod, walletId, userId);
        return money(tile.summary().remainingAmount());
    }

    private BigDecimal sum(Integer walletId, LocalDate from, LocalDate to, CategoryType type) {
        return money(transactionRepository.sumByWalletDateRangeAndType(walletId, from, to, type));
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
    }

    private int inclusiveDays(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return 0;
        }
        return (int) (to.toEpochDay() - from.toEpochDay() + 1);
    }

    private String periodLabel(PeriodType periodType) {
        return switch (periodType) {
            case PAY_CYCLE -> "Current pay cycle";
            case LAST_PAY_CYCLE -> "Last pay cycle";
            case MONTHLY -> "Current month";
            case CUSTOM -> "Custom range";
        };
    }

    private record PeriodWindow(LocalDate startDate, LocalDate endDate) {
    }
}
