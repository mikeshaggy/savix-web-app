package com.mikeshaggy.backend.dashboard.service.period;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class ComparePeriodResolver {

    private final TransactionRepository transactionRepository;

    public PeriodDto resolve(PeriodDto currentPeriod, Integer walletId) {
        return switch (currentPeriod.periodType()) {
            case PAY_CYCLE -> resolveForPayCycle(walletId);
            case LAST_PAY_CYCLE -> resolveForLastPayCycle(walletId);
            case CUSTOM -> resolveForCustom(currentPeriod);
        };
    }

    private PeriodDto resolveForPayCycle(Integer walletId) {
        var salaries = transactionRepository
                .findTop2ByWalletIdAndCategoryNameIgnoreCaseOrderByTransactionDateDesc(walletId, "salary");

        if (salaries.size() >= 2) {
            var latestDate = salaries.get(0).getTransactionDate();
            var previousDate = salaries.get(1).getTransactionDate();
            return new PeriodDto(previousDate, latestDate.minusDays(1), previousDate.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
        }

        return null;
    }

    private PeriodDto resolveForLastPayCycle(Integer walletId) {
        var salaries = transactionRepository
                .findTop3ByWalletIdAndCategoryNameIgnoreCaseOrderByTransactionDateDesc(walletId, "salary");

        if (salaries.size() >= 3) {
            var secondDate = salaries.get(1).getTransactionDate();
            var thirdDate = salaries.get(2).getTransactionDate();
            return new PeriodDto(thirdDate, secondDate.minusDays(1), thirdDate.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
        }

        return null;
    }

    private PeriodDto resolveForCustom(PeriodDto currentPeriod) {
        long days = ChronoUnit.DAYS.between(currentPeriod.startDate(), currentPeriod.endDate()) + 1;
        var compareEnd = currentPeriod.startDate().minusDays(1);
        var compareStart = compareEnd.minusDays(days - 1);
        return new PeriodDto(compareStart, compareEnd, compareStart.plusMonths(1), PeriodType.CUSTOM);
    }
}
