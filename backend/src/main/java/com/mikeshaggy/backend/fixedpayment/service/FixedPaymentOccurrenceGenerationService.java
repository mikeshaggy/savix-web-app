package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentOccurrenceRepository;
import com.mikeshaggy.backend.fixedpayment.repository.FixedPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FixedPaymentOccurrenceGenerationService {

    private static final Map<Cycle, Period> HORIZONS = Map.of(
            Cycle.WEEKLY, Period.ofWeeks(4),
            Cycle.MONTHLY, Period.ofMonths(2),
            Cycle.QUARTERLY, Period.ofMonths(4),
            Cycle.YEARLY, Period.ofMonths(13)
    );

    private final FixedPaymentRepository fixedPaymentRepository;
    private final FixedPaymentOccurrenceRepository occurrenceRepository;
    private final Clock clock;

    @Transactional
    public void ensureOccurrencesGenerated(UUID userId) {
        List<FixedPayment> activePayments = fixedPaymentRepository
                .findAllActiveByUserId(userId, LocalDate.now(clock));

        int generatedCount = 0;

        for (FixedPayment fp : activePayments) {
            LocalDate horizon = LocalDate.now(clock).plus(HORIZONS.get(fp.getCycle()));
            LocalDate lastGenerated = occurrenceRepository
                    .findMaxDueDateByFixedPaymentId(fp.getId())
                    .orElse(fp.getAnchorDate().minusDays(1));

            if (lastGenerated.isBefore(horizon)) {
                generatedCount += generateOccurrences(fp, lastGenerated.plusDays(1), horizon);
            }
        }

        log.info("Fixed payment occurrence generation completed: userId={}, activePayments={}, generatedOccurrences={}",
                userId, activePayments.size(), generatedCount);
    }

    @Transactional
    public void markOverdueOccurrences(UUID userId) {
        List<FixedPayment> activePayments = fixedPaymentRepository
                .findAllActiveByUserId(userId, LocalDate.now(clock));

        List<Integer> fixedPaymentIds = activePayments.stream()
                .map(FixedPayment::getId)
                .toList();

        if (fixedPaymentIds.isEmpty()) {
            return;
        }

        List<FixedPaymentOccurrence> overdueOccurrences = occurrenceRepository
                .findPendingOverdueOccurrences(fixedPaymentIds, LocalDate.now(clock));

        for (FixedPaymentOccurrence occurrence : overdueOccurrences) {
            occurrence.setStatus(OccurrenceStatus.OVERDUE);
        }

        occurrenceRepository.saveAll(overdueOccurrences);

        if (!overdueOccurrences.isEmpty()) {
            log.info("Fixed payment overdue update completed: userId={}, updatedOccurrences={}",
                    userId, overdueOccurrences.size());
        }
    }

    private int generateOccurrences(FixedPayment fp, LocalDate from, LocalDate to) {
        List<LocalDate> dueDates = computeDueDates(fp.getAnchorDate(), fp.getCycle(), from, to);
        if (dueDates.isEmpty()) {
            return 0;
        }

        Set<LocalDate> existingDueDates = new HashSet<>(
                occurrenceRepository.findDueDatesByFixedPaymentIdAndDueDateBetween(fp.getId(), from, to));

        List<FixedPaymentOccurrence> toSave = new ArrayList<>();
        LocalDate activeTo = fp.getActiveTo();
        for (LocalDate dueDate : dueDates) {
            if (activeTo != null && dueDate.isAfter(activeTo)) {
                break;
            }

            if (!existingDueDates.contains(dueDate)) {
                FixedPaymentOccurrence occurrence = FixedPaymentOccurrence.builder()
                        .fixedPayment(fp)
                        .dueDate(dueDate)
                        .expectedAmount(fp.getAmount())
                        .status(OccurrenceStatus.PENDING)
                        .build();
                toSave.add(occurrence);
            }
        }

        if (!toSave.isEmpty()) {
            occurrenceRepository.saveAll(toSave);
            log.info("Fixed payment occurrences generated: fixedPaymentId={}, count={}", fp.getId(), toSave.size());
        }

        return toSave.size();
    }

    List<LocalDate> computeDueDates(LocalDate anchor, Cycle cycle, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();

        LocalDate current = anchor;

        while (current.isBefore(from)) {
            current = advance(current, cycle);
        }

        while (!current.isAfter(to)) {
            dates.add(current);
            current = advance(current, cycle);
        }

        return dates;
    }

    LocalDate advance(LocalDate date, Cycle cycle) {
        return switch (cycle) {
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case QUARTERLY -> date.plusMonths(3);
            case YEARLY -> date.plusYears(1);
        };
    }
}
