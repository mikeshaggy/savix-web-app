package com.mikeshaggy.backend.analytics.breakdown;

import com.mikeshaggy.backend.analytics.breakdown.ImportanceBreakdownDto;
import com.mikeshaggy.backend.analytics.breakdown.ImportanceBreakdownItemDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.repository.ImportanceBreakdownProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.HUNDRED;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.zero;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportanceBreakdownService {

    private static final Map<Importance, Integer> IMPORTANCE_ORDER = Map.of(
            Importance.ESSENTIAL, 0,
            Importance.HAVE_TO_HAVE, 1,
            Importance.NICE_TO_HAVE, 2,
            Importance.SHOULDNT_HAVE, 3,
            Importance.INVESTMENT, 4);

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PeriodService periodService;
    private final Clock clock;

    public ImportanceBreakdownDto getImportanceBreakdown(Integer walletId, UUID userId,
                                                         PeriodType periodType,
                                                         LocalDate startDate, LocalDate endDate) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        PeriodDto period = periodService.resolve(periodType, walletId, userId, startDate, endDate);
        if (period.startDate().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("period must not be in the future");
        }

        List<ImportanceBreakdownProjection> rows = transactionRepository
                .findImportanceBreakdownByWalletDateRangeAndType(
                        walletId, userId, period.startDate(), period.endDate(), CategoryType.EXPENSE);
        // Defensive only: by domain rule, null importance is allowed for income transactions,
        // while expense transactions must have non-null importance.
        List<ImportanceBreakdownProjection> nonNullRows = rows.stream()
                .filter(row -> row.getImportance() != null)
                .toList();
        BigDecimal totalExpenses = nonNullRows.stream()
                .map(row -> money(row.getAmount()))
                .reduce(zero(), BigDecimal::add)
                .setScale(SCALE, ROUNDING);

        List<ImportanceBreakdownItemDto> breakdown = nonNullRows.stream()
                .sorted(Comparator.comparingInt(row ->
                        IMPORTANCE_ORDER.getOrDefault(row.getImportance(), Integer.MAX_VALUE)))
                .map(row -> new ImportanceBreakdownItemDto(
                        row.getImportance(),
                        money(row.getAmount()),
                        share(row.getAmount(), totalExpenses),
                        row.getCount()))
                .toList();

        return new ImportanceBreakdownDto(
                period.periodType(), period.startDate(), period.endDate(), totalExpenses, breakdown);
    }

    private BigDecimal share(BigDecimal amount, BigDecimal totalExpenses) {
        if (totalExpenses.compareTo(BigDecimal.ZERO) == 0) {
            return zero();
        }
        return money(amount).multiply(HUNDRED).divide(totalExpenses, SCALE, ROUNDING);
    }
}
