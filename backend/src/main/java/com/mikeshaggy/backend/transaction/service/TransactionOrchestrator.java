package com.mikeshaggy.backend.transaction.service;

import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentOccurrenceService;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import com.mikeshaggy.backend.transaction.dto.TransactionCreateRequest;
import com.mikeshaggy.backend.transaction.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionOrchestrator {

    private final TransactionService transactionService;
    private final FixedPaymentOccurrenceService fixedPaymentOccurrenceService;

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
}
