package com.mikeshaggy.backend.common.period;

import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.config.FeatureFlags;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LastPayCyclePeriodResolver implements PeriodResolver {

    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final FeatureFlags featureFlags;
    private final PayCycleService payCycleService;
    private final MonthlyPeriodResolver monthlyPeriodResolver;

    @Override
    public PeriodType supports() {
        return PeriodType.LAST_PAY_CYCLE;
    }

    @Override
    public PeriodDto resolve(Integer walletId, UUID userId, LocalDate customStart, LocalDate customEnd, Integer anchorCategoryId) {
        if (featureFlags.payCycleV2()) {
            return resolveFromPayCycleService(walletId, userId);
        }

        LocalDate today = LocalDate.now(clock);

        if (anchorCategoryId != null) {
            List<Transaction> anchorTransactions = transactionRepository
                    .findByWalletUserAndCategoryOrderByTransactionDateDesc(
                            walletId, userId, anchorCategoryId, PageRequest.of(0, 2));

            if (anchorTransactions.size() >= 2) {
                LocalDate latestDate = anchorTransactions.get(0).getTransactionDate();
                LocalDate previousDate = anchorTransactions.get(1).getTransactionDate();
                return PeriodDto.of(previousDate, latestDate.minusDays(1), previousDate.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
            }
        }

        LocalDate firstOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastOfLastMonth = today.withDayOfMonth(1).minusDays(1);
        return PeriodDto.of(firstOfLastMonth, lastOfLastMonth, firstOfLastMonth.plusMonths(1), PeriodType.LAST_PAY_CYCLE);
    }

    /**
     * pay-cycle-v2: the most recent closed cycle of the user's salary wallet (decision T2) — both boundaries are
     * facts and {@code billingEndDate} is the next actual anchor (the current cycle's start). A wallet that is not
     * the salary wallet, or a user without a closed cycle, gets the previous calendar month typed {@code MONTHLY}
     * with {@code salaryWallet = false}.
     */
    private PeriodDto resolveFromPayCycleService(Integer walletId, UUID userId) {
        Optional<PayCycle> last = payCycleService.last(userId);
        if (last.isEmpty() || !Objects.equals(last.get().salaryWalletId(), walletId)) {
            return previousMonthFallback(walletId, userId);
        }
        return closedCyclePeriod(last.get(), PeriodType.LAST_PAY_CYCLE);
    }

    private PeriodDto previousMonthFallback(Integer walletId, UUID userId) {
        LocalDate firstOfLastMonth = LocalDate.now(clock).minusMonths(1).withDayOfMonth(1);
        PeriodDto month = monthlyPeriodResolver.resolve(walletId, userId, firstOfLastMonth, null, null);
        return new PeriodDto(month.startDate(), month.endDate(), month.billingEndDate(), PeriodType.MONTHLY,
                null, null, false);
    }

    /** A CLOSED cycle as a period: end is the day before the next anchor, so the next anchor is {@code end + 1}. */
    static PeriodDto closedCyclePeriod(PayCycle cycle, PeriodType periodType) {
        return new PeriodDto(cycle.start(), cycle.end(), cycle.end().plusDays(1), periodType,
                cycle.state(), null, true);
    }
}
