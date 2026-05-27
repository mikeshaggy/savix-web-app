package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FixedPaymentOccurrenceService {

    private final FixedPaymentOccurrenceRepository occurrenceRepository;
    private final Clock clock;

    @Transactional
    public void markOccurrenceAsPaid(Long occurrenceId, Transaction savedTransaction, UUID userId) {
        FixedPaymentOccurrence occurrence = occurrenceRepository
                .findByIdAndFixedPaymentWalletUserId(occurrenceId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Occurrence not found with id: " + occurrenceId));

        occurrence.setStatus(OccurrenceStatus.PAID);
        occurrence.setPaidAmount(savedTransaction.getAmount());
        occurrence.setPaidAt(LocalDateTime.now(clock));
        occurrence.setTransaction(savedTransaction);
        occurrenceRepository.save(occurrence);

        log.info("Fixed payment occurrence marked paid: occurrenceId={}, transactionId={}, userId={}",
            occurrence.getId(), savedTransaction.getId(), userId);
    }
}
