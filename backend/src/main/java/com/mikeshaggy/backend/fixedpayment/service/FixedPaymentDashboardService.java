package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.common.period.PeriodDto;
import com.mikeshaggy.backend.common.period.PeriodService;
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
import java.util.UUID;

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
    private final Clock clock;

    public FixedTransactionsTileDto getFixedPaymentsTileData(PeriodDto period, Integer walletId, UUID userId) {
        LocalDate today = LocalDate.now(clock);

        List<FixedPayment> activePayments = fixedPaymentRepository
                .findAllActiveByWalletIdAndUserId(walletId, userId, today);
        List<Integer> fixedPaymentIds = activePayments.stream()
                .map(FixedPayment::getId)
                .toList();

        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        BigDecimal currentBalance = wallet.getBalance();

        if (fixedPaymentIds.isEmpty()) {
            return tileAssembler.assembleEmpty(period, currentBalance);
        }

        List<FixedPaymentOccurrence> allInPeriod = occurrenceRepository
                .findAllByFixedPaymentIdsAndDueDateBetween(
                        fixedPaymentIds, period.startDate(), period.billingEndDate());

        List<FixedPaymentOccurrence> overdueAll = occurrenceRepository
                .findByFixedPaymentIdsAndStatus(fixedPaymentIds, OccurrenceStatus.OVERDUE);

        BigDecimal totalIncome = transactionService.sumIncomeByWalletIdAndDateRange(
                walletId, userId, period.startDate(), period.endDate());

        return tileAssembler.assemble(
                period, allInPeriod, overdueAll,
                totalIncome, currentBalance, fixedPaymentIds.size()
        );
    }

    public FixedTransactionsTileDto getFixedPaymentsTileData(PeriodDto period, Integer walletId, UUID userId,
                                                             LocalDate asOfDate) {
        Wallet wallet = walletService.getWalletEntityByIdForUser(walletId, userId);
        return getFixedPaymentsTileData(period, wallet, userId, asOfDate);
    }

    public FixedTransactionsTileDto getFixedPaymentsTileData(PeriodDto period, Wallet wallet, UUID userId,
                                                             LocalDate asOfDate) {
        LocalDate effectiveAsOfDate = asOfDate == null ? LocalDate.now(clock) : asOfDate;

        List<FixedPayment> activePayments = fixedPaymentRepository
                .findAllActiveInPeriodByWalletIdAndUserId(
                        wallet.getId(), userId, period.startDate(), period.billingEndDate());
        List<Integer> fixedPaymentIds = activePayments.stream()
                .map(FixedPayment::getId)
                .toList();

        BigDecimal currentBalance = wallet.getBalance();

        if (fixedPaymentIds.isEmpty()) {
            return tileAssembler.assembleEmpty(period, currentBalance);
        }

        List<FixedPaymentOccurrence> allInPeriod = occurrenceRepository
                .findAllByFixedPaymentIdsAndDueDateBetween(
                        fixedPaymentIds, period.startDate(), period.billingEndDate())
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
                wallet.getId(), userId, period.startDate(), period.endDate());

        return tileAssembler.assemble(
                period, allInPeriod, overdueAll,
                totalIncome, currentBalance, fixedPaymentIds.size(), effectiveAsOfDate
        );
    }

    public FixedTransactionsTileDto getFixedPaymentsTileDataForCurrentPeriod(Integer walletId, UUID userId) {
        PeriodDto period = periodService.getCurrentPeriod(userId, walletId);
        return getFixedPaymentsTileData(period, walletId, userId);
    }

    private boolean isWithinFixedPaymentActiveDates(FixedPaymentOccurrence occurrence) {
        FixedPayment fixedPayment = occurrence.getFixedPayment();
        LocalDate dueDate = occurrence.getDueDate();
        return !dueDate.isBefore(fixedPayment.getActiveFrom())
                && (fixedPayment.getActiveTo() == null || !dueDate.isAfter(fixedPayment.getActiveTo()));
    }
}
