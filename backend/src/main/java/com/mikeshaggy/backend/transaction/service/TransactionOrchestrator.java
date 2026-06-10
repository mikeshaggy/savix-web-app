package com.mikeshaggy.backend.transaction.service;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.category.domain.CategoryType;
import com.mikeshaggy.backend.category.service.CategoryService;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentOccurrenceService;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.dto.TransactionCreateRequest;
import com.mikeshaggy.backend.transaction.dto.TransactionResponse;
import com.mikeshaggy.backend.transaction.dto.TransactionUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owns transaction operations that also touch fixed payment occurrences, so the
 * cross-entity wiring lives in one place and {@link TransactionService} and the
 * fixed-payment services stay free of circular dependencies.
 */
@Service
@RequiredArgsConstructor
public class TransactionOrchestrator {

    private final TransactionService transactionService;
    private final FixedPaymentOccurrenceService fixedPaymentOccurrenceService;
    private final CategoryService categoryService;

    @Transactional
    public TransactionResponse createTransaction(TransactionCreateRequest request, UUID userId) {
        Transaction savedTransaction = transactionService.createTransactionEntity(request, userId);

        if (request.occurrenceId() != null) {
            fixedPaymentOccurrenceService.markOccurrenceAsPaid(
                    request.occurrenceId(), savedTransaction, userId
            );
        }

        return TransactionResponse.from(savedTransaction);
    }

    @Transactional
    public TransactionResponse updateTransaction(Long id, TransactionUpdateRequest request, UUID userId) {
        Transaction existing = transactionService.getEntityForUser(id, userId);
        FixedPaymentOccurrence occurrence = existing.getFixedPaymentOccurrence();

        if (occurrence != null) {
            validateLinkedTransactionEdit(request, occurrence, userId);
        }

        TransactionResponse response = transactionService.updateTransaction(id, request, userId);

        if (occurrence != null) {
            // Amount may have changed; keep the occurrence's recorded paid amount in step.
            fixedPaymentOccurrenceService.syncPaidAmount(occurrence, request.amount());
        }

        return response;
    }

    @Transactional
    public void deleteTransaction(Long id, UUID userId) {
        Transaction existing = transactionService.getEntityForUser(id, userId);
        FixedPaymentOccurrence occurrence = existing.getFixedPaymentOccurrence();

        if (occurrence != null) {
            // Return the occurrence to its unpaid state before removing the
            // transaction so it does not linger as a "paid" row with no backing
            // transaction.
            fixedPaymentOccurrenceService.unlinkAndReset(occurrence);
        }

        transactionService.deleteTransaction(id, userId);
    }

    private void validateLinkedTransactionEdit(TransactionUpdateRequest request,
                                               FixedPaymentOccurrence occurrence,
                                               UUID userId) {
        Category newCategory = categoryService.getCategoryEntityByIdForUser(request.categoryId(), userId);
        if (newCategory.getType() == CategoryType.INCOME) {
            throw new IllegalArgumentException(
                    "Cannot change a transaction linked to a fixed payment into an income transaction. "
                            + "Unlink it from the fixed payment first.");
        }

        Integer fixedPaymentWalletId = occurrence.getFixedPayment().getWallet().getId();
        if (!request.walletId().equals(fixedPaymentWalletId)) {
            throw new IllegalArgumentException(
                    "Cannot move a transaction linked to a fixed payment to a different wallet. "
                            + "Unlink it from the fixed payment first.");
        }
    }
}
