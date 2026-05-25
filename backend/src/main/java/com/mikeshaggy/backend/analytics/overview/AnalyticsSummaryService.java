package com.mikeshaggy.backend.analytics.overview;

import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionDto;
import com.mikeshaggy.backend.analytics.forecast.SpendingProjectionService;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.dto.ResolvedPeriods;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.transaction.repository.CategoryBreakdownProjection;
import com.mikeshaggy.backend.transaction.repository.HeatmapProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsSummaryService {

    private final SpendingProjectionService spendingProjectionService;
    private final PeriodService periodService;
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;

    public AnalyticsSummaryDto getSummary(Integer walletId, UUID userId,
                                          PeriodType periodType,
                                          LocalDate startDate, LocalDate endDate) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        // ── 1. Projection data (covers forecast KPIs + period window) ─────────
        SpendingProjectionDto proj = spendingProjectionService.getSpendingProjection(
                walletId, userId, periodType, startDate, endDate);

        // ── 2. Top spending category ──────────────────────────────────────────
        List<CategoryBreakdownProjection> cats = transactionRepository
                .findIncludedCategorySpendByWalletUserAndDateRange(
                        walletId, userId, proj.startDate(), proj.endDate(), CategoryType.EXPENSE);
        CategoryBreakdownProjection topCat = cats.isEmpty() ? null : cats.get(0);

        // ── 3. Daily stats from heatmap rows ──────────────────────────────────
        List<HeatmapProjection> heatmapRows = transactionRepository
                .findHeatmapByWalletDateRangeAndType(
                        walletId, proj.startDate(), proj.endDate(), CategoryType.EXPENSE);

        Map<LocalDate, BigDecimal> dailyTotals = heatmapRows.stream()
                .collect(Collectors.groupingBy(
                        HeatmapProjection::getDate,
                        Collectors.reducing(BigDecimal.ZERO,
                                HeatmapProjection::getAmount, BigDecimal::add)));

        LocalDate highestSpendingDay = null;
        BigDecimal highestSpendingDayAmount = null;
        if (!dailyTotals.isEmpty()) {
            Map.Entry<LocalDate, BigDecimal> peak = dailyTotals.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElseThrow();
            highestSpendingDay = peak.getKey();
            highestSpendingDayAmount = money(peak.getValue());
        }
        int activeDays = dailyTotals.size();

        // ── 4. Comparison (vs previous equivalent period) ─────────────────────
        ResolvedPeriods resolvedPeriods = periodService.resolvePeriods(
                periodType, walletId, userId, startDate, endDate);
        PeriodDto compare = resolvedPeriods.compare();

        BigDecimal compareExpenses = money(transactionRepository.sumByWalletUserDateRangeAndType(
                walletId, userId, compare.startDate(), compare.endDate(), CategoryType.EXPENSE));
        boolean comparisonAvailable = compareExpenses.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal expensesDeltaPercent = null;
        if (comparisonAvailable) {
            expensesDeltaPercent = proj.expensesToDate()
                    .subtract(compareExpenses)
                    .multiply(HUNDRED)
                    .divide(compareExpenses, SCALE, ROUNDING);
        }

        // ── 5. Savings rate (projected) ───────────────────────────────────────
        BigDecimal savingsRate = null;
        if (proj.incomeForPeriod().compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = proj.incomeForPeriod()
                    .subtract(proj.projectedPeriodExpenses())
                    .multiply(HUNDRED)
                    .divide(proj.incomeForPeriod(), SCALE, ROUNDING);
        }

        // ── 6. Status ─────────────────────────────────────────────────────────
        OverviewStatus status = computeStatus(proj);

        return new AnalyticsSummaryDto(
                status,
                proj.startDate(),
                proj.endDate(),
                proj.daysInPeriod(),
                proj.daysElapsed(),
                proj.daysRemaining(),
                proj.projectionAvailable(),
                proj.projectedEndBalance(),
                proj.safeToSpendToday(),
                proj.dailyBurnRate(),
                savingsRate,
                proj.projectedPeriodExpenses(),
                proj.incomeForPeriod(),
                topCat != null ? topCat.getCategoryId() : null,
                topCat != null ? topCat.getName() : null,
                topCat != null ? topCat.getEmoji() : null,
                topCat != null ? money(topCat.getAmount()) : null,
                highestSpendingDay,
                highestSpendingDayAmount,
                activeDays,
                expensesDeltaPercent,
                comparisonAvailable);
    }

    // ── Status logic ──────────────────────────────────────────────────────────

    private OverviewStatus computeStatus(SpendingProjectionDto proj) {
        if (!proj.projectionAvailable()) {
            return OverviewStatus.NEUTRAL;
        }
        if (proj.projectedEndBalance().compareTo(BigDecimal.ZERO) < 0
                || proj.safeToSpendToday().compareTo(BigDecimal.ZERO) < 0) {
            return OverviewStatus.CRITICAL;
        }
        if (proj.incomeForPeriod().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ninetyPct = proj.incomeForPeriod()
                    .multiply(new BigDecimal("0.90"));
            if (proj.projectedPeriodExpenses().compareTo(ninetyPct) > 0) {
                return OverviewStatus.WARNING;
            }
        }
        return OverviewStatus.GOOD;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
    }
}
