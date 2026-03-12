package com.mikeshaggy.backend.dashboard.service.period;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ComparePeriodResolver {

    private final TransactionRepository transactionRepository;

    public PeriodDto resolve(PeriodDto currentPeriod, Integer walletId, Integer anchorCategoryId) {
        return switch (currentPeriod.periodType()) {
            case PAY_CYCLE -> resolveForPayCycle(walletId, anchorCategoryId);
            case LAST_PAY_CYCLE -> resolveForLastPayCycle(walletId, anchorCategoryId);
            case CUSTOM -> resolveForCustom(currentPeriod);
        };
    }

    private PeriodDto resolveForPayCycle(Integer walletId, Integer anchorCategoryId) {
        List<Transaction> anchorTransactions = findAnchorTransactions(walletId, anchorCategoryId, 2);

        if (anchorTransactions.size() >= 2) {
            LocalDate latestDate = anchorTransactions.get(0).getTransactionDate();
            LocalDate previousDate = anchorTransactions.get(1).getTransactionDate();
            return new PeriodDto(previousDate, latestDate.minusDays(1), previousDate.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
        }

        return null;
    }

    private PeriodDto resolveForLastPayCycle(Integer walletId, Integer anchorCategoryId) {
        List<Transaction> anchorTransactions = findAnchorTransactions(walletId, anchorCategoryId, 3);

        if (anchorTransactions.size() >= 3) {
            LocalDate secondDate = anchorTransactions.get(1).getTransactionDate();
            LocalDate thirdDate = anchorTransactions.get(2).getTransactionDate();
            return new PeriodDto(thirdDate, secondDate.minusDays(1), thirdDate.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
        }

        return null;
    }

    private PeriodDto resolveForCustom(PeriodDto currentPeriod) {
        long days = ChronoUnit.DAYS.between(currentPeriod.startDate(), currentPeriod.endDate()) + 1;
        LocalDate compareEnd = currentPeriod.startDate().minusDays(1);
        LocalDate compareStart = compareEnd.minusDays(days - 1);
        return new PeriodDto(compareStart, compareEnd, compareStart.plusMonths(1), PeriodType.CUSTOM);
    }

    private List<Transaction> findAnchorTransactions(Integer walletId, Integer anchorCategoryId, int count) {
        if (anchorCategoryId == null) {
            return List.of();
        }
        return transactionRepository.findByWalletIdAndCategoryIdOrderByTransactionDateDesc(
                walletId, anchorCategoryId, PageRequest.of(0, count));
    }
}
