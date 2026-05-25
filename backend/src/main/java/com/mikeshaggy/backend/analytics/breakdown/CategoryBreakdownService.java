package com.mikeshaggy.backend.analytics.breakdown;

import com.mikeshaggy.backend.analytics.breakdown.CategoryBreakdownDto;
import com.mikeshaggy.backend.analytics.breakdown.CategoryBreakdownItemDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.dashboard.service.PeriodService;
import com.mikeshaggy.backend.transaction.repository.CategoryBreakdownProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryBreakdownService {

    private final TransactionRepository transactionRepository;
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

        List<CategoryBreakdownProjection> rows = transactionRepository
                .findCategoryBreakdownByWalletDateRangeAndType(
                        walletId, period.startDate(), period.endDate(), CategoryType.EXPENSE);
        BigDecimal totalExpenses = rows.stream()
                .map(row -> money(row.getAmount()))
                .reduce(BigDecimal.ZERO.setScale(SCALE, ROUNDING), BigDecimal::add)
                .setScale(SCALE, ROUNDING);

        List<CategoryBreakdownItemDto> categories = rows.stream()
                .map(row -> new CategoryBreakdownItemDto(
                        row.getCategoryId(),
                        row.getName(),
                        row.getEmoji(),
                        money(row.getAmount()),
                        share(row.getAmount(), totalExpenses),
                        row.getTransactionCount()))
                .toList();

        return new CategoryBreakdownDto(
                period.periodType(), period.startDate(), period.endDate(), totalExpenses, categories);
    }

    private BigDecimal share(BigDecimal amount, BigDecimal totalExpenses) {
        if (totalExpenses.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return money(amount).multiply(HUNDRED).divide(totalExpenses, SCALE, ROUNDING);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
    }
}
