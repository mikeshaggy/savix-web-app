package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.common.paycycle.CycleState;
import com.mikeshaggy.backend.common.paycycle.PayCycle;
import com.mikeshaggy.backend.common.paycycle.PayCycleService;
import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
import com.mikeshaggy.backend.common.period.PeriodType;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.dto.FixedTransactionsTileDto;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fixed-payment tile data for one committed window (decision T4): occurrences due in
 * {@code [period.startDate(), period.endDate()]} — the day before the expected payday is the last day of the
 * cycle, the payday itself belongs to the next one — so {@code billingEndDate} is never used as a bound.
 * A cycle awaiting its salary keeps the window open through today (T5). Every caller (dashboard,
 * {@code /api/fixed-payments/tile}, forecast) goes through the same selection so they agree on the committed set.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FixedPaymentDashboardService {

    private final FixedPaymentRepository fixedPaymentRepository;
    private final FixedPaymentOccurrenceRepository occurrenceRepository;
    private final FixedPaymentTileAssembler tileAssembler;
    private final WalletService walletService;
    private final TransactionService transactionService;
    private final PeriodService periodService;
    private final PayCycleService payCycleService;
    private final Clock clock;

    public FixedTransactionsTileDto getFixedPaymentsTileData(PeriodDto period, Integer walletId, UUID userId) {
        return getFixedPaymentsTileData(period, walletId, userId, null);
    }

    public FixedTransactionsTileDto getFixedPaymentsTileData(PeriodDto period, Integer walletId, UUID userId,
                                                             LocalDate asOfDate) {
        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        return getFixedPaymentsTileData(period, wallet, userId, asOfDate);
    }

    public FixedTransactionsTileDto getFixedPaymentsTileData(PeriodDto period, Wallet wallet, UUID userId,
                                                             LocalDate asOfDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveAsOfDate = asOfDate == null ? today : asOfDate;
        PeriodDto window = committedWindow(period, today);

        List<FixedPayment> activePayments = fixedPaymentRepository
                .findAllActiveInPeriodByWalletIdAndUserId(
                        wallet.getId(), userId, window.startDate(), window.endDate());
        List<Integer> fixedPaymentIds = activePayments.stream()
                .map(FixedPayment::getId)
                .toList();

        BigDecimal currentBalance = wallet.getBalance();

        if (fixedPaymentIds.isEmpty()) {
            return tileAssembler.assembleEmpty(window, currentBalance);
        }

        List<FixedPaymentOccurrence> allInPeriod = occurrenceRepository
                .findAllByFixedPaymentIdsAndDueDateBetween(
                        fixedPaymentIds, window.startDate(), window.endDate())
                .stream()
                .filter(this::isWithinFixedPaymentActiveDates)
                .toList();

        List<FixedPaymentOccurrence> overdueAll = occurrenceRepository
                .findByFixedPaymentIdsAndStatus(fixedPaymentIds, OccurrenceStatus.OVERDUE)
                .stream()
                .filter(this::isWithinFixedPaymentActiveDates)
                .filter(o -> !o.getDueDate().isAfter(effectiveAsOfDate))
                .toList();

        BigDecimal totalIncome = transactionService.sumIncomeByWalletIdAndDateRange(
                wallet.getId(), userId, window.startDate(), window.endDate());

        return tileAssembler.assemble(
                window, allInPeriod, overdueAll,
                totalIncome, currentBalance, fixedPaymentIds.size(), effectiveAsOfDate
        );
    }

    /**
     * The {@code /api/fixed-payments/tile} path: the open cycle of the user's salary wallet, resolved from
     * {@link PayCycleService}. A wallet that is not the salary wallet, or a user without a cycle, has no committed
     * semantics (T7) and gets an empty tile over the calendar month typed {@code MONTHLY}.
     */
    public FixedTransactionsTileDto getFixedPaymentsTileDataForCurrentPeriod(Integer walletId, UUID userId) {
        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        Optional<PayCycle> current = payCycleService.current(userId);
        if (current.isEmpty() || !Objects.equals(current.get().salaryWalletId(), walletId)) {
            return tileAssembler.assembleEmpty(reportingMonth(walletId, userId), wallet.getBalance());
        }
        PayCycle cycle = current.get();
        PeriodDto period = new PeriodDto(cycle.start(), cycle.end(), cycle.expectedNextAnchor(), PeriodType.PAY_CYCLE,
                cycle.state(), cycle.expectedNextAnchor(), true);
        return getFixedPaymentsTileData(period, wallet, userId, LocalDate.now(clock));
    }

    /**
     * The committed window of {@code period}: the period itself ({@code endDate = expectedNextAnchor − 1} under
     * pay-cycle-v2), extended through today while the cycle is {@link CycleState#AWAITING_SALARY} so occurrences
     * due between the expected and the actual payday are not orphaned until the new anchor closes the cycle.
     */
    private PeriodDto committedWindow(PeriodDto period, LocalDate today) {
        if (period.cycleState() != CycleState.AWAITING_SALARY || !today.isAfter(period.endDate())) {
            return period;
        }
        return new PeriodDto(period.startDate(), today, period.billingEndDate(), period.periodType(),
                period.cycleState(), period.expectedNextAnchorDate(), period.salaryWallet());
    }

    private PeriodDto reportingMonth(Integer walletId, UUID userId) {
        PeriodDto month = periodService.resolve(PeriodType.MONTHLY, walletId, userId, null, null);
        return new PeriodDto(month.startDate(), month.endDate(), month.billingEndDate(), PeriodType.MONTHLY,
                null, null, false);
    }

    /**
     * A paid occurrence is history and always counts; an unpaid one only while its fixed payment is active on the
     * due date, so a shortened {@code activeTo} drops stale pending rows without erasing what was already paid.
     */
    private boolean isWithinFixedPaymentActiveDates(FixedPaymentOccurrence occurrence) {
        if (occurrence.getStatus() == OccurrenceStatus.PAID) {
            return true;
        }
        FixedPayment fixedPayment = occurrence.getFixedPayment();
        LocalDate dueDate = occurrence.getDueDate();
        return !dueDate.isBefore(fixedPayment.getActiveFrom())
                && (fixedPayment.getActiveTo() == null || !dueDate.isAfter(fixedPayment.getActiveTo()));
    }
}
