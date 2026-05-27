package com.mikeshaggy.backend.fixedpayment.maintenance;

import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import com.mikeshaggy.backend.fixedpayment.service.FixedPaymentOccurrenceGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FixedPaymentOccurrenceMaintenanceService {

    private final FixedPaymentOccurrenceGenerationService generationService;
    private final FixedPaymentRepository fixedPaymentRepository;
    private final Clock clock;

    @Transactional
    public void prepareOccurrencesAfterFixedPaymentMutation(UUID userId) {
        prepareOccurrencesForUser(userId, "fixed-payment-mutation");
    }

    @Transactional
    public void prepareAllActiveFixedPayments() {
        List<UUID> userIds = fixedPaymentRepository.findActiveUserIds(LocalDate.now(clock));
        for (UUID userId : userIds) {
            prepareOccurrencesForUser(userId, "scheduled");
        }
        log.info("Fixed payment scheduled maintenance completed: usersProcessed={}", userIds.size());
    }

    private void prepareOccurrencesForUser(UUID userId, String trigger) {
        generationService.ensureOccurrencesGenerated(userId);
        generationService.deletePendingOccurrencesAfterActiveTo(userId);
        generationService.markOverdueOccurrences(userId);
        log.info("Fixed payment occurrence maintenance completed: userId={}, trigger={}", userId, trigger);
    }
}
