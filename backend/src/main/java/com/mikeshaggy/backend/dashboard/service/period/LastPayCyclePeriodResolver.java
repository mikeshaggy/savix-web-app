package com.mikeshaggy.backend.dashboard.service.period;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.dashboard.dto.PeriodType;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repo.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

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
    public PeriodDto resolve(Integer walletId, LocalDate customStart, LocalDate customEnd) {
        LocalDate today = LocalDate.now(clock);

        List<Transaction> salaries = transactionRepository
                .findTop2ByWalletIdAndCategoryNameIgnoreCaseOrderByTransactionDateDesc(walletId, "salary");

        if (salaries.size() >= 2) {
            LocalDate latestSalaryDate = salaries.get(0).getTransactionDate();
            LocalDate previousSalaryDate = salaries.get(1).getTransactionDate();
            return new PeriodDto(previousSalaryDate, latestSalaryDate.minusDays(1), PeriodType.LAST_PAY_CYCLE);
        }

        // Fallback: previous calendar month
        LocalDate firstOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastOfLastMonth = today.withDayOfMonth(1).minusDays(1);
        return new PeriodDto(firstOfLastMonth, lastOfLastMonth, PeriodType.LAST_PAY_CYCLE);
    }
}
