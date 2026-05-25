package com.mikeshaggy.backend.dashboard.period;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PayCyclePeriodResolver implements PeriodResolver {

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Override
    public PeriodType supports() {
        return PeriodType.PAY_CYCLE;
    }

    @Override
    public PeriodDto resolve(Integer walletId, UUID userId, LocalDate customStart, LocalDate customEnd, Integer anchorCategoryId) {
        LocalDate today = LocalDate.now(clock);

        LocalDate latestAnchorDate = findLatestAnchorTransactionDate(walletId, userId, anchorCategoryId);

        if (latestAnchorDate != null) {
            return new PeriodDto(latestAnchorDate, today, latestAnchorDate.plusMonths(1), PeriodType.PAY_CYCLE);
        }

        return new PeriodDto(today.withDayOfMonth(1), today, today.withDayOfMonth(1).plusMonths(1), PeriodType.PAY_CYCLE);
    }

    LocalDate findLatestAnchorTransactionDate(Integer walletId, UUID userId, Integer anchorCategoryId) {
        if (anchorCategoryId == null) {
            return null;
        }

        return transactionRepository
                .findByWalletUserAndCategoryOrderByTransactionDateDesc(
                        walletId, userId, anchorCategoryId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(Transaction::getTransactionDate)
                .orElse(null);
    }
}
