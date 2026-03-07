package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.*;
import com.mikeshaggy.backend.fixedpayment.repo.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repo.FixedPaymentRepository;
import com.mikeshaggy.backend.transaction.service.TransactionService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.service.WalletService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FixedPaymentService {

    private final FixedPaymentRepository fixedPaymentRepository;
    private final FixedPaymentOccurrenceRepository occurrenceRepository;
    private final FixedPaymentOccurrenceGenerationService generationService;
    private final CurrentUserProvider currentUserProvider;
    private final WalletService walletService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;

    @Transactional
    public FixedPaymentDto createFixedPayment(CreateFixedPaymentRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();

        Wallet wallet = walletService.getWalletOrThrowForUser(request.walletId(), userId);

        Category category = categoryService.getCategoryOrThrowForUser(request.categoryId(), userId);

        FixedPayment fp = FixedPayment.builder()
                .wallet(wallet)
                .category(category)
                .title(request.title())
                .amount(request.amount())
                .anchorDate(request.anchorDate())
                .cycle(request.cycle())
                .activeFrom(request.activeFrom() != null ? request.activeFrom() : LocalDate.now())
                .activeTo(request.activeTo())
                .notes(request.notes())
                .build();

        fp = fixedPaymentRepository.save(fp);

        generationService.ensureOccurrencesGenerated(userId);

        return FixedPaymentDto.from(fp);
    }

    @Transactional
    public FixedPaymentDto updateFixedPayment(Integer id, UpdateFixedPaymentRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();

        FixedPayment fp = getFixedPaymentOrThrowForUser(id, userId);

        boolean amountChanged = !fp.getAmount().equals(request.amount());
        boolean cycleChanged = !fp.getCycle().equals(request.cycle());
        boolean anchorChanged = !fp.getAnchorDate().equals(request.anchorDate());

        if (amountChanged || cycleChanged || anchorChanged) {
            List<FixedPaymentOccurrence> futurePending = occurrenceRepository
                    .findFuturePendingByFixedPaymentId(fp.getId(), LocalDate.now());
            occurrenceRepository.deleteAll(futurePending);
        }

        fp.setTitle(request.title());
        fp.setAmount(request.amount());
        fp.setAnchorDate(request.anchorDate());
        fp.setCycle(request.cycle());
        fp.setNotes(request.notes());
        fp.setActiveTo(request.activeTo());

        fp = fixedPaymentRepository.save(fp);

        if (amountChanged || cycleChanged || anchorChanged) {
            generationService.ensureOccurrencesGenerated(userId);
        }

        return FixedPaymentDto.from(fp);
    }

    @Transactional
    public void deactivateFixedPayment(Integer id) {
        UUID userId = currentUserProvider.getCurrentUserId();

        FixedPayment fp = getFixedPaymentOrThrowForUser(id, userId);

        fp.setActiveTo(LocalDate.now());

        List<FixedPaymentOccurrence> futurePending = occurrenceRepository
                .findFuturePendingByFixedPaymentId(fp.getId(), LocalDate.now());
        occurrenceRepository.deleteAll(futurePending);

        fixedPaymentRepository.save(fp);
    }

    public List<FixedPaymentDto> getAllFixedPayments(Integer walletId) {
        UUID userId = currentUserProvider.getCurrentUserId();

        return fixedPaymentRepository.findAllByWalletIdAndUserId(walletId, userId).stream()
                .map(FixedPaymentDto::from)
                .toList();
    }

    @Transactional
    public void prepareOccurrencesForDashboard() {
        UUID userId = currentUserProvider.getCurrentUserId();
        generationService.ensureOccurrencesGenerated(userId);
        generationService.markOverdueOccurrences(userId);
    }

    public FixedTransactionsTileDto getFixedPaymentsTileData(PeriodDto period, Integer walletId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        LocalDate today = LocalDate.now();

        List<FixedPayment> activePayments = fixedPaymentRepository
                .findAllActiveByWalletIdAndUserId(walletId, userId, today);
        List<Integer> fixedPaymentIds = activePayments.stream()
                .map(FixedPayment::getId)
                .toList();

        if (fixedPaymentIds.isEmpty()) {
            return buildEmptyTileDto(period, walletId);
        }

        List<FixedPaymentOccurrence> allInPeriod = occurrenceRepository
                .findAllByFixedPaymentIdsAndDueDateBetween(
                        fixedPaymentIds, period.startDate(), period.billingEndDate());

        List<FixedPaymentOccurrence> paidInPeriod = allInPeriod.stream()
                .filter(o -> o.getStatus() == OccurrenceStatus.PAID)
                .toList();

        List<FixedPaymentOccurrence> pendingInPeriod = allInPeriod.stream()
                .filter(o -> o.getStatus() == OccurrenceStatus.PENDING
                        && !o.getDueDate().isBefore(today))
                .toList();

        List<FixedPaymentOccurrence> overdueAll = occurrenceRepository
                .findByFixedPaymentIdsAndStatus(fixedPaymentIds, OccurrenceStatus.OVERDUE);

        List<FixedPaymentOccurrence> upcomingInPeriod = allInPeriod.stream()
                .filter(o -> o.getStatus() == OccurrenceStatus.PENDING
                        && !o.getDueDate().isBefore(today)
                        && !o.getDueDate().isAfter(period.billingEndDate()))
                .sorted(Comparator.comparing(FixedPaymentOccurrence::getDueDate))
                .toList();

        BigDecimal totalIncome = transactionService.calculateTotalIncomeForWalletAndPeriod(walletId, period);

        BigDecimal plannedAmount = sumExpectedAmount(allInPeriod);

        double fixedRatio = BigDecimal.ZERO.compareTo(totalIncome) == 0
                ? 0.0
                : plannedAmount.multiply(BigDecimal.valueOf(100))
                .divide(totalIncome, 2, RoundingMode.HALF_UP)
                .doubleValue();

        FixedSummaryDto summary = new FixedSummaryDto(
                plannedAmount,
                allInPeriod.size(),
                sumExpectedAmount(paidInPeriod),
                paidInPeriod.size(),
                sumExpectedAmount(pendingInPeriod),
                pendingInPeriod.size(),
                sumExpectedAmount(overdueAll),
                overdueAll.size(),
                fixedRatio
        );

        LocalDate nextDueDate = upcomingInPeriod.stream()
                .map(FixedPaymentOccurrence::getDueDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        String nextDueName = upcomingInPeriod.stream()
                .filter(o -> o.getDueDate().equals(nextDueDate))
                .findFirst()
                .map(o -> o.getFixedPayment().getTitle())
                .orElse(null);

        FixedPaymentOccurrence biggestUpcoming = upcomingInPeriod.stream()
                .max(Comparator.comparing(FixedPaymentOccurrence::getExpectedAmount))
                .orElse(null);

        double paidPct = allInPeriod.isEmpty()
                ? 0.0
                : BigDecimal.valueOf(paidInPeriod.size())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(allInPeriod.size()), 2, RoundingMode.HALF_UP)
                .doubleValue();

        FixedProgressDto progress = new FixedProgressDto(
                paidInPeriod.size(),
                allInPeriod.size(),
                paidPct,
                nextDueDate,
                nextDueName,
                biggestUpcoming != null ? biggestUpcoming.getFixedPayment().getTitle() : null,
                biggestUpcoming != null ? biggestUpcoming.getExpectedAmount() : null,
                fixedPaymentIds.size()
        );

        Wallet wallet = walletService.getWalletOrThrow(walletId);
        BigDecimal currentBalance = wallet.getBalance();
        BigDecimal unpaidTotal = summary.remainingAmount().add(summary.overdueAmount());
        BigDecimal balanceAfterFixed = currentBalance.subtract(unpaidTotal);
        boolean isAtRisk = balanceAfterFixed.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal shortfallAmount = isAtRisk ? balanceAfterFixed.abs() : null;

        RiskIndicatorDto riskIndicator = new RiskIndicatorDto(isAtRisk, shortfallAmount);

        List<FixedOccurrenceRowDto> overdueRows = overdueAll.stream()
                .map(FixedOccurrenceRowDto::from)
                .toList();

        List<FixedOccurrenceRowDto> upcomingRows = upcomingInPeriod.stream()
                .map(FixedOccurrenceRowDto::from)
                .toList();

        List<FixedOccurrenceRowDto> paidRows = paidInPeriod.stream()
                .map(FixedOccurrenceRowDto::from)
                .toList();

        return new FixedTransactionsTileDto(
                period.startDate(),
                period.endDate(),
                period.billingEndDate(),
                summary,
                progress,
                currentBalance,
                balanceAfterFixed,
                riskIndicator,
                overdueRows,
                upcomingRows,
                paidRows
        );
    }

    private FixedPayment getFixedPaymentOrThrowForUser(Integer id, UUID userId) {
        FixedPayment fp = fixedPaymentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Fixed payment not found with id: " + id));

        if (!fp.getWallet().getUser().getId().equals(userId)) {
            throw new EntityNotFoundException("Fixed payment not found with id: " + id);
        }

        return fp;
    }

    private BigDecimal sumExpectedAmount(List<FixedPaymentOccurrence> occurrences) {
        return occurrences.stream()
                .map(FixedPaymentOccurrence::getExpectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private FixedTransactionsTileDto buildEmptyTileDto(PeriodDto period, Integer walletId) {
        Wallet wallet = walletService.getWalletOrThrow(walletId);
        BigDecimal currentBalance = wallet.getBalance();

        FixedSummaryDto summary = new FixedSummaryDto(
                BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 0,
                0.0
        );

        FixedProgressDto progress = new FixedProgressDto(
                0, 0, 0.0,
                null, null,
                null, null,
                0
        );

        RiskIndicatorDto riskIndicator = new RiskIndicatorDto(false, null);

        return new FixedTransactionsTileDto(
                period.startDate(),
                period.endDate(),
                period.billingEndDate(),
                summary,
                progress,
                currentBalance,
                currentBalance,
                riskIndicator,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public List<FixedPayment> getActiveFixedPaymentsForUserForDate(UUID userId, LocalDate date) {
        return fixedPaymentRepository.findAllActiveByUserId(userId, date);
    }
}
