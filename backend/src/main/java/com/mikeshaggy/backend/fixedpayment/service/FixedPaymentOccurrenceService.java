package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.common.exception.ConflictException;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.dto.FixedOccurrenceRowDto;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FixedPaymentOccurrenceService {

    private final FixedPaymentOccurrenceRepository occurrenceRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    @Transactional
    public void markOccurrenceAsPaid(Long occurrenceId, Transaction savedTransaction, UUID userId) {
        FixedPaymentOccurrence occurrence = occurrenceRepository
                .findByIdAndFixedPaymentWalletUserId(occurrenceId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Occurrence not found with id: " + occurrenceId));

        applyPaid(occurrence, savedTransaction);
        occurrenceRepository.save(occurrence);

        log.info("Fixed payment occurrence marked paid: occurrenceId={}, transactionId={}, userId={}",
            occurrence.getId(), savedTransaction.getId(), userId);
    }

    /**
     * Links an already-persisted transaction to an unpaid occurrence (the
     * "I created the transaction first" flow). Validation mirrors the invariants
     * enforced elsewhere; amount/date/category mismatches are intentionally NOT
     * blocked.
     */
    @Transactional
    public FixedOccurrenceRowDto linkExistingTransaction(Long occurrenceId, Long transactionId, UUID userId) {
        FixedPaymentOccurrence occurrence = occurrenceRepository
                .findByIdAndFixedPaymentWalletUserId(occurrenceId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Occurrence not found with id: " + occurrenceId));

        if (occurrence.getStatus() == OccurrenceStatus.PAID || occurrence.getTransaction() != null) {
            throw new ConflictException("Occurrence is already linked to a transaction");
        }

        Transaction transaction = transactionRepository
                .findByIdAndWalletUserId(transactionId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Transaction not found with id: " + transactionId));

        if (transaction.getCategory().getType() != CategoryType.EXPENSE) {
            throw new IllegalArgumentException(
                    "Only expense transactions can be linked to a fixed payment");
        }
        if (transaction.getFixedPaymentOccurrence() != null) {
            throw new ConflictException(
                    "Transaction is already linked to a fixed payment occurrence");
        }
        Integer fixedPaymentWalletId = occurrence.getFixedPayment().getWallet().getId();
        if (!transaction.getWallet().getId().equals(fixedPaymentWalletId)) {
            throw new IllegalArgumentException(
                    "Transaction wallet must match the fixed payment wallet");
        }

        applyPaid(occurrence, transaction);
        occurrenceRepository.save(occurrence);

        log.info("Fixed payment occurrence linked to existing transaction: "
                + "occurrenceId={}, transactionId={}, userId={}",
            occurrence.getId(), transaction.getId(), userId);

        return FixedOccurrenceRowDto.from(occurrence, LocalDate.now(clock));
    }

    /**
     * Unlinks the transaction currently paying an occurrence (explicit API
     * unlink). Reuses {@link #unlinkAndReset(FixedPaymentOccurrence)} for the
     * reset/status logic.
     */
    @Transactional
    public FixedOccurrenceRowDto unlinkByOccurrenceId(Long occurrenceId, UUID userId) {
        FixedPaymentOccurrence occurrence = occurrenceRepository
                .findByIdAndFixedPaymentWalletUserId(occurrenceId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Occurrence not found with id: " + occurrenceId));

        if (occurrence.getTransaction() == null) {
            throw new ConflictException("Occurrence is not linked to any transaction");
        }

        unlinkAndReset(occurrence);
        return FixedOccurrenceRowDto.from(occurrence, LocalDate.now(clock));
    }

    /**
     * Shared "mark as paid" mutation used by both the create-with-occurrenceId
     * flow and the link-existing-transaction flow. Keeps both sides of the 1:1
     * in sync so the in-memory graph reflects the link immediately.
     */
    private void applyPaid(FixedPaymentOccurrence occurrence, Transaction transaction) {
        occurrence.setStatus(OccurrenceStatus.PAID);
        occurrence.setPaidAmount(transaction.getAmount());
        occurrence.setPaidAt(LocalDateTime.now(clock));
        occurrence.setTransaction(transaction);
        transaction.setFixedPaymentOccurrence(occurrence);
    }

    /**
     * Detaches the transaction that paid this occurrence and returns the
     * occurrence to its unpaid state. Used when the linking transaction is
     * deleted (or explicitly unlinked) so the occurrence does not linger as a
     * "paid" row with no backing transaction.
     *
     * <p>Status is recomputed from the due date: OVERDUE when the due date is
     * before today, PENDING otherwise (mirrors {@code markOverdueOccurrences}).
     */
    @Transactional
    public void unlinkAndReset(FixedPaymentOccurrence occurrence) {
        Transaction linked = occurrence.getTransaction();
        if (linked != null) {
            linked.setFixedPaymentOccurrence(null);
        }
        occurrence.setTransaction(null);
        occurrence.setPaidAmount(null);
        occurrence.setPaidAt(null);
        occurrence.setStatus(occurrence.getDueDate().isBefore(LocalDate.now(clock))
                ? OccurrenceStatus.OVERDUE
                : OccurrenceStatus.PENDING);
        occurrenceRepository.save(occurrence);

        log.info("Fixed payment occurrence unlinked: occurrenceId={}, status={}",
            occurrence.getId(), occurrence.getStatus());
    }

    /**
     * Keeps a linked occurrence's {@code paidAmount} in step with its backing
     * transaction's amount after the transaction is edited.
     */
    @Transactional
    public void syncPaidAmount(FixedPaymentOccurrence occurrence, BigDecimal newAmount) {
        occurrence.setPaidAmount(newAmount);
        occurrenceRepository.save(occurrence);
    }
}
