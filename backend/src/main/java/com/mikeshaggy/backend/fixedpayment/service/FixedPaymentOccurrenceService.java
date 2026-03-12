package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.enums.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.repo.FixedPaymentOccurrenceRepository;
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
    private final FixedPaymentOccurrenceGenerationService generationService;
    private final Clock clock;

    @Transactional
    public void prepareOccurrencesForDashboard(UUID userId) {
        generationService.ensureOccurrencesGenerated(userId);
        generationService.markOverdueOccurrences(userId);
    }

    @Transactional
    public void markOccurrenceAsPaid(Long occurrenceId, Transaction savedTransaction, UUID userId) {
        FixedPaymentOccurrence occurrence = occurrenceRepository.findById(occurrenceId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Occurrence not found with id: " + occurrenceId));

        if (!occurrence.getFixedPayment().getWallet().getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Occurrence does not belong to current user");
        }

        occurrence.setStatus(OccurrenceStatus.PAID);
        occurrence.setPaidAmount(savedTransaction.getAmount());
        occurrence.setPaidAt(LocalDateTime.now(clock));
        occurrence.setTransaction(savedTransaction);
        occurrenceRepository.save(occurrence);

        log.info("Marked occurrence id: {} as PAID with transaction id: {}",
                occurrence.getId(), savedTransaction.getId());
    }
}
