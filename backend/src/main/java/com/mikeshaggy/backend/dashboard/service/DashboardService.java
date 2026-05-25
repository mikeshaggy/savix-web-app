package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.dashboard.dto.*;
import com.mikeshaggy.backend.dashboard.service.calculator.SummaryCalculator;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.percentageChange;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final PeriodService periodService;
    private final TransactionService transactionService;
    private final SummaryCalculator summaryCalculator;
    private final WalletService walletService;
    private final FixedPaymentDashboardService fixedPaymentDashboardService;
    private final CategoryAggregationService categoryAggregationService;

    public DashboardService(PeriodService periodService,
                            TransactionService transactionService,
                            SummaryCalculator summaryCalculator,
                            WalletService walletService,
                            FixedPaymentDashboardService fixedPaymentDashboardService,
                            CategoryAggregationService categoryAggregationService) {
        this.periodService = periodService;
        this.transactionService = transactionService;
        this.summaryCalculator = summaryCalculator;
        this.walletService = walletService;
        this.fixedPaymentDashboardService = fixedPaymentDashboardService;
        this.categoryAggregationService = categoryAggregationService;
    }

    public DashboardData getDashboardData(UUID userId, Integer walletId, LocalDate startDate,
                                          LocalDate endDate, PeriodType periodType) {

        ResolvedPeriods periods = periodService.resolvePeriods(periodType, walletId, userId, startDate, endDate);
        String walletName = walletService.getWalletEntityByIdForUser(walletId, userId).getName();

        List<Transaction> currentTransactions = transactionService
                .getTransactionsForWalletAndPeriod(walletId, userId, periods.primary());
        List<Transaction> compareTransactions = periods.compare() != null
                ? transactionService.getTransactionsForWalletAndPeriod(walletId, userId, periods.compare())
                : Collections.emptyList();

        SummaryDto summary = summaryCalculator.calculate(currentTransactions, compareTransactions);
        List<CategorySpendingDto> topCategories = periods.compare() == null
                ? categoryAggregationService.aggregateExpenses(
                        walletId, userId,
                        periods.primary().startDate(), periods.primary().endDate(),
                        CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)
                        .categories().stream()
                        .limit(5)
                        .map(category -> new CategorySpendingDto(
                                category.name(),
                                category.amount(),
                                category.share(),
                                percentageChange(category.amount(), BigDecimal.ZERO)))
                        .toList()
                : categoryAggregationService
                        .aggregateExpenseComparison(
                                walletId, userId,
                                periods.primary().startDate(), periods.primary().endDate(),
                                periods.compare().startDate(), periods.compare().endDate(),
                                CategoryAggregationMode.INCLUDED_IN_TOP_CATEGORIES)
                        .stream()
                        .limit(5)
                        .map(category -> new CategorySpendingDto(
                                category.category().name(),
                                category.category().amount(),
                                category.category().share(),
                                percentageChange(category.category().amount(), category.compareAmount())))
                        .toList();

        FixedTransactionsTileDto fixedPaymentsTile = fixedPaymentDashboardService
                .getFixedPaymentsTileData(periods.primary(), walletId, userId);

        return new DashboardData(periods.primary(), summary, topCategories, walletName, fixedPaymentsTile);
    }
}
