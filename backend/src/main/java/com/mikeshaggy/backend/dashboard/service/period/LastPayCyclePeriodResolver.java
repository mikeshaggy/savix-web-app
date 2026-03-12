package com.mikeshaggy.backend.dashboard.service.period;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LastPayCyclePeriodResolver implements PeriodResolver {

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Override
    public PeriodType supports() {
        return PeriodType.LAST_PAY_CYCLE;
    }

    @Override
    public PeriodDto resolve(Integer walletId, UUID userId, LocalDate customStart, LocalDate customEnd, Integer anchorCategoryId) {
        LocalDate today = LocalDate.now(clock);

        if (anchorCategoryId != null) {
            List<Transaction> anchorTransactions = transactionRepository
                    .findByWalletIdAndCategoryIdOrderByTransactionDateDesc(walletId, anchorCategoryId, PageRequest.of(0, 2));

            if (anchorTransactions.size() >= 2) {
                LocalDate latestDate = anchorTransactions.get(0).getTransactionDate();
                LocalDate previousDate = anchorTransactions.get(1).getTransactionDate();
                return new PeriodDto(previousDate, latestDate.minusDays(1), previousDate.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
            }
        }

        LocalDate firstOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastOfLastMonth = today.withDayOfMonth(1).minusDays(1);
        return new PeriodDto(firstOfLastMonth, lastOfLastMonth, firstOfLastMonth.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
    }
}
