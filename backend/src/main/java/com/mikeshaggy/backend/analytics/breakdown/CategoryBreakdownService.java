package com.mikeshaggy.backend.analytics.breakdown;

import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationMode;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationResult;
import com.mikeshaggy.backend.analytics.aggregation.CategoryAggregationService;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryBreakdownService {

    private final CategoryAggregationService categoryAggregationService;
    private final WalletService walletService;
    private final PeriodService periodService;
    private final Clock clock;

    public CategoryBreakdownDto getCategoryBreakdown(Integer walletId, UUID userId,
                                                     PeriodType periodType,
                                                     LocalDate startDate, LocalDate endDate) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        PeriodDto period = periodService.resolve(periodType, walletId, userId, startDate, endDate);
        if (period.startDate().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("period must not be in the future");
        }

        CategoryAggregationResult result = categoryAggregationService.aggregateExpenses(
                walletId, userId, period.startDate(), period.endDate(),
                CategoryAggregationMode.ALL);

        return new CategoryBreakdownDto(
                period.periodType(),
                period.startDate(),
                period.endDate(),
                result.total(),
                result.categories().stream()
                        .map(category -> new CategoryBreakdownItemDto(
                                category.categoryId(),
                                category.name(),
                                category.emoji(),
                                category.amount(),
                                category.share(),
                                category.transactionCount()))
                        .toList());
    }
}
