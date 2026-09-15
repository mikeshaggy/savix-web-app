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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PayCyclePeriodResolver implements PeriodResolver {

    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final FeatureFlags featureFlags;
    private final PayCycleService payCycleService;
    private final MonthlyPeriodResolver monthlyPeriodResolver;

    @Override
    public PeriodType supports() {
        return PeriodType.PAY_CYCLE;
    }

    @Override
    public PeriodDto resolve(Integer walletId, UUID userId, LocalDate customStart, LocalDate customEnd, Integer anchorCategoryId) {
        if (featureFlags.payCycleV2()) {
            return resolveFromPayCycleService(walletId, userId);
        }

        LocalDate today = LocalDate.now(clock);

        LocalDate latestAnchorDate = findLatestAnchorTransactionDate(walletId, userId, anchorCategoryId);

        if (latestAnchorDate != null) {
            return PeriodDto.of(latestAnchorDate, today, latestAnchorDate.plusMonths(1), PeriodType.PAY_CYCLE);
        }

        return PeriodDto.of(today.withDayOfMonth(1), today, today.withDayOfMonth(1).plusMonths(1), PeriodType.PAY_CYCLE);
    }

    /**
     * pay-cycle-v2: the open cycle of the user's salary wallet (decisions T3, T6, T7). The end is
     * {@code expectedNextAnchor − 1} — the whole cycle, never today — and the state is reported as resolved.
     * A wallet that is not the salary wallet, or a user without any cycle, gets the calendar month typed
     * {@code MONTHLY} with {@code salaryWallet = false}: never a calendar month labelled as a pay cycle.
     */
    private PeriodDto resolveFromPayCycleService(Integer walletId, UUID userId) {
        Optional<PayCycle> current = payCycleService.current(userId);
        if (current.isEmpty() || !Objects.equals(current.get().salaryWalletId(), walletId)) {
            return monthlyFallback(walletId, userId);
        }
        PayCycle cycle = current.get();
        return new PeriodDto(cycle.start(), cycle.end(), cycle.expectedNextAnchor(), PeriodType.PAY_CYCLE,
                cycle.state(), cycle.expectedNextAnchor(), true);
    }

    private PeriodDto monthlyFallback(Integer walletId, UUID userId) {
        PeriodDto month = monthlyPeriodResolver.resolve(walletId, userId, null, null, null);
        return new PeriodDto(month.startDate(), month.endDate(), month.billingEndDate(), PeriodType.MONTHLY,
                null, null, false);
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
