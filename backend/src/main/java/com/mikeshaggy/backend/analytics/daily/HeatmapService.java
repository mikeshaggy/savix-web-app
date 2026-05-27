package com.mikeshaggy.backend.analytics.daily;

import com.mikeshaggy.backend.analytics.daily.HeatmapCategoryDto;
import com.mikeshaggy.backend.analytics.daily.HeatmapDayDto;
import com.mikeshaggy.backend.analytics.daily.HeatmapResponseDto;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.transaction.repository.HeatmapProjection;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.mikeshaggy.backend.common.calculation.CalculationUtils.ROUNDING;
import static com.mikeshaggy.backend.common.calculation.CalculationUtils.SCALE;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.money;
import static com.mikeshaggy.backend.common.calculation.MoneyMath.zero;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeatmapService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final PeriodService periodService;

    public HeatmapResponseDto getHeatmap(Integer walletId, UUID userId,
                                         PeriodType periodType, LocalDate startDate, LocalDate endDate) {
        walletService.getWalletEntityByIdForUser(walletId, userId);

        PeriodDto period = periodService.resolve(periodType, walletId, userId, startDate, endDate);
        LocalDate from = period.startDate();
        LocalDate to = period.endDate();

        List<HeatmapProjection> rows = transactionRepository.findHeatmapByWalletDateRangeAndType(
                walletId, userId, from, to, CategoryType.EXPENSE);
        Map<LocalDate, List<HeatmapProjection>> rowsByDate = rows.stream()
                .collect(Collectors.groupingBy(HeatmapProjection::getDate));

        List<HeatmapDayDto> days = new ArrayList<>();
        BigDecimal maxDayTotal = zero();

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<HeatmapProjection> dayRows = rowsByDate.getOrDefault(date, List.of());

            List<HeatmapCategoryDto> categories = dayRows.stream()
                    .map(row -> new HeatmapCategoryDto(
                            row.getCategoryId(),
                            row.getCategoryName(),
                            row.getEmoji(),
                            money(row.getAmount()),
                            count(row.getTransactions())))
                    .toList();

            BigDecimal total = categories.stream()
                    .map(HeatmapCategoryDto::amount)
                    .reduce(zero(), BigDecimal::add)
                    .setScale(SCALE, ROUNDING);
            long transactions = categories.stream()
                    .mapToLong(HeatmapCategoryDto::transactions)
                    .sum();

            if (total.compareTo(maxDayTotal) > 0) {
                maxDayTotal = total;
            }

            days.add(new HeatmapDayDto(date, total, transactions, categories));
        }

        return new HeatmapResponseDto(from, to, days, maxDayTotal);
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }
}
