package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.dashboard.dto.*;
import com.mikeshaggy.backend.dashboard.service.calculator.SummaryCalculator;
import com.mikeshaggy.backend.dashboard.service.calculator.TopCategoriesCalculator;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentDashboardService;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final PeriodService periodService;
    private final TransactionService transactionService;
    private final SummaryCalculator summaryCalculator;
    private final TopCategoriesCalculator topCategoriesCalculator;
    private final WalletService walletService;
    private final FixedPaymentDashboardService fixedPaymentDashboardService;

    public DashboardService(PeriodService periodService,
                            TransactionService transactionService,
                            SummaryCalculator summaryCalculator,
                            TopCategoriesCalculator topCategoriesCalculator,
                            WalletService walletService,
                            FixedPaymentDashboardService fixedPaymentDashboardService) {
        this.periodService = periodService;
        this.transactionService = transactionService;
        this.summaryCalculator = summaryCalculator;
        this.topCategoriesCalculator = topCategoriesCalculator;
        this.walletService = walletService;
        this.fixedPaymentDashboardService = fixedPaymentDashboardService;
    }

    public DashboardData getDashboardData(UUID userId, Integer walletId, LocalDate startDate,
                                          LocalDate endDate, PeriodType periodType) {

        ResolvedPeriods periods = periodService.resolvePeriods(periodType, walletId, userId, startDate, endDate);

        List<Transaction> currentTransactions = transactionService
                .getTransactionsForWalletAndPeriod(walletId, periods.primary());
        List<Transaction> compareTransactions = periods.compare() != null
                ? transactionService.getTransactionsForWalletAndPeriod(walletId, periods.compare())
                : Collections.emptyList();

        SummaryDto summary = summaryCalculator.calculate(currentTransactions, compareTransactions);
        List<CategorySpendingDto> topCategories = topCategoriesCalculator
                .calculate(currentTransactions, compareTransactions);

        String walletName = walletService.getWalletEntityByIdForUser(walletId, userId).getName();

        FixedTransactionsTileDto fixedPaymentsTile = fixedPaymentDashboardService
                .getFixedPaymentsTileData(periods.primary(), walletId, userId);

        return new DashboardData(periods.primary(), summary, topCategories, walletName, fixedPaymentsTile);
    }
}
