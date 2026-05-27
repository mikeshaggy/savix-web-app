package com.mikeshaggy.backend.common.period;

import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ComparePeriodResolver {

    private final TransactionRepository transactionRepository;

    public PeriodDto resolve(PeriodDto currentPeriod, Integer walletId, UUID userId, Integer anchorCategoryId) {
        return switch (currentPeriod.periodType()) {
            case PAY_CYCLE -> resolveForPayCycle(currentPeriod, walletId, userId, anchorCategoryId);
            case LAST_PAY_CYCLE -> resolveForLastPayCycle(currentPeriod, walletId, userId, anchorCategoryId);
            case CUSTOM -> resolveForCustom(currentPeriod);
            case MONTHLY -> resolveForMonthly(currentPeriod);
        };
    }

    private PeriodDto resolveForPayCycle(PeriodDto currentPeriod, Integer walletId,
                                         UUID userId, Integer anchorCategoryId) {
        List<Transaction> anchorTransactions = findAnchorTransactions(walletId, userId, anchorCategoryId, 2);

        if (anchorTransactions.size() >= 2) {
            LocalDate latestDate = anchorTransactions.get(0).getTransactionDate();
            LocalDate previousDate = anchorTransactions.get(1).getTransactionDate();
            return new PeriodDto(previousDate, latestDate.minusDays(1), previousDate.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
        }

        return resolveForCustom(currentPeriod);
    }

    private PeriodDto resolveForLastPayCycle(PeriodDto currentPeriod, Integer walletId,
                                             UUID userId, Integer anchorCategoryId) {
        List<Transaction> anchorTransactions = findAnchorTransactions(walletId, userId, anchorCategoryId, 3);

        if (anchorTransactions.size() >= 3) {
            LocalDate secondDate = anchorTransactions.get(1).getTransactionDate();
            LocalDate thirdDate = anchorTransactions.get(2).getTransactionDate();
            return new PeriodDto(thirdDate, secondDate.minusDays(1), thirdDate.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
        }

        return resolveForCustom(currentPeriod);
    }

    private PeriodDto resolveForCustom(PeriodDto currentPeriod) {
        long days = InclusiveDateRange.daysBetween(currentPeriod.startDate(), currentPeriod.endDate());
        LocalDate compareEnd = currentPeriod.startDate().minusDays(1);
        LocalDate compareStart = compareEnd.minusDays(days - 1);
        return new PeriodDto(compareStart, compareEnd, compareStart.plusMonths(1), PeriodType.CUSTOM);
    }

    private PeriodDto resolveForMonthly(PeriodDto currentPeriod) {
        LocalDate compareStart = currentPeriod.startDate().minusMonths(1);
        LocalDate compareEnd = currentPeriod.startDate().minusDays(1);
        return new PeriodDto(compareStart, compareEnd, currentPeriod.startDate(), PeriodType.MONTHLY);
    }

    private List<Transaction> findAnchorTransactions(Integer walletId, UUID userId,
                                                     Integer anchorCategoryId, int count) {
        if (anchorCategoryId == null) {
            return List.of();
        }
        return transactionRepository.findByWalletUserAndCategoryOrderByTransactionDateDesc(
                walletId, userId, anchorCategoryId, PageRequest.of(0, count));
    }
}
