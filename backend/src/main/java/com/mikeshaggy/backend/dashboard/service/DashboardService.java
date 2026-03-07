package com.mikeshaggy.backend.dashboard.service;

import com.mikeshaggy.backend.dashboard.dto.*;
import com.mikeshaggy.backend.dashboard.service.calculator.SummaryCalculator;
import com.mikeshaggy.backend.dashboard.service.calculator.TopCategoriesCalculator;
import com.mikeshaggy.backend.dashboard.service.period.ComparePeriodResolver;
import com.mikeshaggy.backend.dashboard.service.period.PeriodResolver;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentService;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final Map<PeriodType, PeriodResolver> periodResolvers;
    private final ComparePeriodResolver comparePeriodResolver;
    private final TransactionService transactionService;
    private final SummaryCalculator summaryCalculator;
    private final TopCategoriesCalculator topCategoriesCalculator;
    private final WalletService walletService;
    private final FixedPaymentService fixedPaymentService;

    public DashboardService(List<PeriodResolver> resolvers,
                            ComparePeriodResolver comparePeriodResolver,
                            TransactionService transactionService,
                            SummaryCalculator summaryCalculator,
                            TopCategoriesCalculator topCategoriesCalculator,
                            WalletService walletService,
                            FixedPaymentService fixedPaymentService) {
        this.periodResolvers = resolvers.stream()
                .collect(Collectors.toMap(PeriodResolver::supports, Function.identity()));
        this.comparePeriodResolver = comparePeriodResolver;
        this.transactionService = transactionService;
        this.summaryCalculator = summaryCalculator;
        this.topCategoriesCalculator = topCategoriesCalculator;
        this.walletService = walletService;
        this.fixedPaymentService = fixedPaymentService;
    }

    public DashboardData getDashboardData(Integer walletId, String startDate,
                                          String endDate, PeriodType periodType) {

        LocalDate customStart = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate customEnd = endDate != null ? LocalDate.parse(endDate) : null;

        PeriodResolver resolver = periodResolvers.get(periodType);
        if (resolver == null) {
            throw new IllegalArgumentException("Unsupported period type: " + periodType);
        }
        PeriodDto period = resolver.resolve(walletId, customStart, customEnd);

        PeriodDto comparePeriod = comparePeriodResolver.resolve(period, walletId);

        List<Transaction> currentTransactions = transactionService
                .getTransactionsForWalletAndPeriod(walletId, period);
        List<Transaction> compareTransactions = transactionService
                .getTransactionsForWalletAndComparePeriod(walletId, comparePeriod);

        SummaryDto summary = summaryCalculator.calculate(currentTransactions, compareTransactions);
        List<CategorySpendingDto> topCategories = topCategoriesCalculator
                .calculate(currentTransactions, compareTransactions);

        String walletName = walletService.getWalletNameOrThrow(walletId);

        FixedTransactionsTileDto fixedPaymentsTile = fixedPaymentService.getFixedPaymentsTileData(period, walletId);

        return new DashboardData(period, summary, topCategories, walletName, fixedPaymentsTile);
    }
}
