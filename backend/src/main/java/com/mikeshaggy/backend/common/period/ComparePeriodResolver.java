package com.mikeshaggy.backend.common.period;

import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.InclusiveDateRange;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.config.FeatureFlags;
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
    private final FeatureFlags featureFlags;
    private final PayCycleService payCycleService;

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
        if (featureFlags.payCycleV2()) {
            return previousClosedCycle(currentPeriod, userId);
        }

        List<Transaction> anchorTransactions = findAnchorTransactions(walletId, userId, anchorCategoryId, 2);

        if (anchorTransactions.size() >= 2) {
            LocalDate latestDate = anchorTransactions.get(0).getTransactionDate();
            LocalDate previousDate = anchorTransactions.get(1).getTransactionDate();
            return PeriodDto.of(previousDate, latestDate.minusDays(1), previousDate.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
        }

        return resolveForCustom(currentPeriod);
    }

    private PeriodDto resolveForLastPayCycle(PeriodDto currentPeriod, Integer walletId,
                                             UUID userId, Integer anchorCategoryId) {
        if (featureFlags.payCycleV2()) {
            return previousClosedCycle(currentPeriod, userId);
        }

        List<Transaction> anchorTransactions = findAnchorTransactions(walletId, userId, anchorCategoryId, 3);

        if (anchorTransactions.size() >= 3) {
            LocalDate secondDate = anchorTransactions.get(1).getTransactionDate();
            LocalDate thirdDate = anchorTransactions.get(2).getTransactionDate();
            return PeriodDto.of(thirdDate, secondDate.minusDays(1), thirdDate.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
        }

        return resolveForCustom(currentPeriod);
    }

    /**
     * pay-cycle-v2: the closed cycle immediately before {@code currentPeriod} — the cycle that ended the day before
     * the period's start anchor — typed {@code LAST_PAY_CYCLE}. {@code historyAsOf(start)} sees the anchors up to
     * and including the start anchor, so its first entry is exactly that cycle; a PAY_CYCLE or LAST_PAY_CYCLE
     * period only reaches this method after its own resolver confirmed the salary wallet. Without a closed cycle
     * the previous same-length window is used, as before.
     */
    private PeriodDto previousClosedCycle(PeriodDto currentPeriod, UUID userId) {
        return payCycleService.historyAsOf(userId, currentPeriod.startDate(), 1).stream()
                .findFirst()
                .map(cycle -> LastPayCyclePeriodResolver.closedCyclePeriod(cycle, PeriodType.LAST_PAY_CYCLE))
                .orElseGet(() -> resolveForCustom(currentPeriod));
    }

    private PeriodDto resolveForCustom(PeriodDto currentPeriod) {
        long days = InclusiveDateRange.daysBetween(currentPeriod.startDate(), currentPeriod.endDate());
        LocalDate compareEnd = currentPeriod.startDate().minusDays(1);
        LocalDate compareStart = compareEnd.minusDays(days - 1);
        return PeriodDto.of(compareStart, compareEnd, compareStart.plusMonths(1), PeriodType.CUSTOM);
    }

    private PeriodDto resolveForMonthly(PeriodDto currentPeriod) {
        LocalDate compareStart = currentPeriod.startDate().minusMonths(1);
        LocalDate compareEnd = currentPeriod.startDate().minusDays(1);
        return PeriodDto.of(compareStart, compareEnd, currentPeriod.startDate(), PeriodType.MONTHLY);
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
