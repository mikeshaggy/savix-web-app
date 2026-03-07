package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import com.mikeshaggy.backend.fixedpayment.repo.FixedPaymentOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private final FixedPaymentOccurrenceRepository occurrenceRepository;
    private final FixedPaymentService fixedPaymentService;

    @Transactional
    public void ensureOccurrencesGenerated(UUID userId) {
        List<FixedPayment> activePayments = fixedPaymentService
                .getActiveFixedPaymentsForUserForDate(userId, LocalDate.now());

        for (FixedPayment fp : activePayments) {
            LocalDate horizon = LocalDate.now().plus(HORIZONS.get(fp.getCycle()));
            LocalDate lastGenerated = occurrenceRepository
                    .findMaxDueDateByFixedPaymentId(fp.getId())
                    .orElse(fp.getAnchorDate().minusDays(1));

            if (lastGenerated.isBefore(horizon)) {
                generateOccurrences(fp, lastGenerated.plusDays(1), horizon);
            }
        }
    }

    @Transactional
    public void markOverdueOccurrences(UUID userId) {
        List<Integer> fixedPaymentIds = fixedPaymentService
                .getActiveFixedPaymentsForUserForDate(userId, LocalDate.now())
                .stream()
                .map(FixedPayment::getId)
                .toList();

        if (fixedPaymentIds.isEmpty()) {
            return;
        }

        List<FixedPaymentOccurrence> overdueOccurrences = occurrenceRepository
                .findPendingOverdueOccurrences(fixedPaymentIds, LocalDate.now());

        overdueOccurrences.forEach(occurrence -> {
            occurrence.setStatus(OccurrenceStatus.OVERDUE);
        });

        occurrenceRepository.saveAll(overdueOccurrences);
    }

    private void generateOccurrences(FixedPayment fp, LocalDate from, LocalDate to) {
        List<LocalDate> dueDates = computeDueDates(fp.getAnchorDate(), fp.getCycle(), from, to);

        List<FixedPaymentOccurrence> toSave = new ArrayList<>();
        for (LocalDate dueDate : dueDates) {
            if (!occurrenceRepository.existsByFixedPaymentIdAndDueDate(fp.getId(), dueDate)) {
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
        }
    }

    private List<LocalDate> computeDueDates(LocalDate anchor, Cycle cycle, LocalDate from, LocalDate to) {
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

    private LocalDate advance(LocalDate date, Cycle cycle) {
        return switch (cycle) {
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case QUARTERLY -> date.plusMonths(3);
            case YEARLY -> date.plusYears(1);
        };
    }
}
