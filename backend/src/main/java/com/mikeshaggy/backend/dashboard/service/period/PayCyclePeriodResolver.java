package com.mikeshaggy.backend.dashboard.service.period;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

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
    public PeriodDto resolve(Integer walletId, LocalDate customStart, LocalDate customEnd) {
        LocalDate today = LocalDate.now(clock);

        LocalDate latestSalaryDate = transactionRepository
                .findTopByWalletIdAndCategoryNameIgnoreCaseOrderByTransactionDateDesc(walletId, "salary")
                .map(Transaction::getTransactionDate)
                .orElse(null);

        if (latestSalaryDate != null) {
            return new PeriodDto(latestSalaryDate, today, latestSalaryDate.plusMonths(1), PeriodType.PAY_CYCLE);
        }

        return new PeriodDto(today.withDayOfMonth(1), today, today.withDayOfMonth(1).plusMonths(1), PeriodType.PAY_CYCLE);
    }
}
