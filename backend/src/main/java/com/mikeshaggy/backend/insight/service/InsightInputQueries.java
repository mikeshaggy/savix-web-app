package com.mikeshaggy.backend.insight.service;

import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.insight.detector.InsightContext.FixedPaymentHistory;
import com.mikeshaggy.backend.insight.detector.InsightContext.OccurrenceSnapshot;
import com.mikeshaggy.backend.insight.detector.InsightContext.WalletSnapshot;
import com.mikeshaggy.backend.wallet.domain.SavingsWallets;
import com.mikeshaggy.backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The Stage 7.3 context inputs that have no owning read service: plain repository reads, mapped to snapshots before
 * the transaction ends (no lazy entity state reaches a detector).
 * <p>
 * Each method is its own read-only transaction on purpose. Detection runs with {@code NOT_SUPPORTED}; a repository
 * query method called there without a transaction binds an EntityManager to that scope, which keeps its connection
 * until detection ends — the next transactional input would then take a second connection, and concurrent
 * evaluations starve the pool ({@code InsightEvaluationSingleConnectionTest}). Inside a transaction the connection is
 * returned when the method returns.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsightInputQueries {

    /** Occurrence window of {@code FixedPaymentAmountDrift}: two yearly occurrences back, the committed window ahead. */
    static final int OCCURRENCE_HISTORY_MONTHS = 25;
    static final int OCCURRENCE_AHEAD_DAYS = 62;

    private final WalletRepository walletRepository;
    private final FixedPaymentRepository fixedPaymentRepository;
    private final FixedPaymentOccurrenceRepository occurrenceRepository;

    /** {@link SavingsWallets#single} relative to {@code salaryWalletId}. */
    public Optional<WalletSnapshot> savingsWallet(UUID userId, Integer salaryWalletId) {
        return SavingsWallets.single(walletRepository.findByUserIdAndIsFundFalse(userId), salaryWalletId)
                .map(savings -> new WalletSnapshot(savings.getId(), savings.getName(), savings.getBalance()));
    }

    /** Active fixed payments of the wallet (activeTo unset or ≥ today) with their occurrences in the history window. */
    public List<FixedPaymentHistory> fixedPayments(Integer walletId, UUID userId, LocalDate today) {
        List<FixedPayment> active = fixedPaymentRepository.findAllByWalletIdAndUserId(walletId, userId).stream()
                .filter(payment -> payment.getActiveTo() == null || !payment.getActiveTo().isBefore(today))
                .sorted(Comparator.comparing(FixedPayment::getId))
                .toList();
        if (active.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<OccurrenceSnapshot>> byPayment = occurrenceRepository
                .findAllByFixedPaymentIdsAndDueDateBetween(
                        active.stream().map(FixedPayment::getId).toList(),
                        today.minusMonths(OCCURRENCE_HISTORY_MONTHS),
                        today.plusDays(OCCURRENCE_AHEAD_DAYS))
                .stream()
                .sorted(Comparator.comparing(FixedPaymentOccurrence::getDueDate)
                        .thenComparing(FixedPaymentOccurrence::getId))
                .collect(Collectors.groupingBy(occurrence -> occurrence.getFixedPayment().getId(),
                        Collectors.mapping(occurrence -> new OccurrenceSnapshot(occurrence.getId(),
                                occurrence.getDueDate(), occurrence.getStatus(), occurrence.getExpectedAmount(),
                                occurrence.getPaidAmount()), Collectors.toList())));
        return active.stream()
                .map(payment -> new FixedPaymentHistory(payment.getId(), payment.getTitle(), payment.getAmount(),
                        payment.getCategory().getId(), byPayment.getOrDefault(payment.getId(), List.of())))
                .toList();
    }
}
