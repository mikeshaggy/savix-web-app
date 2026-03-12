package com.mikeshaggy.backend.fixedpayment.service;

import com.mikeshaggy.backend.dashboard.dto.PeriodDto;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.dto.*;
import com.mikeshaggy.backend.fixedpayment.enums.OccurrenceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
class FixedPaymentTileAssembler {

    private final Clock clock;

    FixedTransactionsTileDto assemble(
            PeriodDto period,
            List<FixedPaymentOccurrence> allInPeriod,
            List<FixedPaymentOccurrence> overdueAll,
            BigDecimal totalIncome,
            BigDecimal currentBalance,
            int activeFixedCount
    ) {
        LocalDate today = LocalDate.now(clock);

        List<FixedPaymentOccurrence> paidInPeriod = allInPeriod.stream()
                .filter(o -> o.getStatus() == OccurrenceStatus.PAID)
                .toList();

        List<FixedPaymentOccurrence> pendingInPeriod = allInPeriod.stream()
                .filter(o -> o.getStatus() == OccurrenceStatus.PENDING
                        && !o.getDueDate().isBefore(today))
                .toList();

        List<FixedPaymentOccurrence> upcomingInPeriod = allInPeriod.stream()
                .filter(o -> o.getStatus() == OccurrenceStatus.PENDING
                        && !o.getDueDate().isBefore(today)
                        && !o.getDueDate().isAfter(period.billingEndDate()))
                .sorted(Comparator.comparing(FixedPaymentOccurrence::getDueDate))
                .toList();

        FixedSummaryDto summary = buildSummary(allInPeriod, paidInPeriod, pendingInPeriod, overdueAll, totalIncome);
        FixedProgressDto progress = buildProgress(allInPeriod, paidInPeriod, upcomingInPeriod, activeFixedCount);
        RiskIndicatorDto riskIndicator = buildRiskIndicator(summary, currentBalance);

        BigDecimal unpaidTotal = summary.remainingAmount().add(summary.overdueAmount());
        BigDecimal balanceAfterFixed = currentBalance.subtract(unpaidTotal);

        List<FixedOccurrenceRowDto> overdueRows = overdueAll.stream()
                .map(o -> FixedOccurrenceRowDto.from(o, today)).toList();
        List<FixedOccurrenceRowDto> upcomingRows = upcomingInPeriod.stream()
                .map(o -> FixedOccurrenceRowDto.from(o, today)).toList();
        List<FixedOccurrenceRowDto> paidRows = paidInPeriod.stream()
                .map(o -> FixedOccurrenceRowDto.from(o, today)).toList();

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

    FixedTransactionsTileDto assembleEmpty(PeriodDto period, BigDecimal currentBalance) {
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

    private FixedSummaryDto buildSummary(
            List<FixedPaymentOccurrence> allInPeriod,
            List<FixedPaymentOccurrence> paidInPeriod,
            List<FixedPaymentOccurrence> pendingInPeriod,
            List<FixedPaymentOccurrence> overdueAll,
            BigDecimal totalIncome
    ) {
        BigDecimal plannedAmount = sumExpectedAmount(allInPeriod);

        double fixedRatio = BigDecimal.ZERO.compareTo(totalIncome) == 0
                ? 0.0
                : plannedAmount.multiply(BigDecimal.valueOf(100))
                        .divide(totalIncome, 2, RoundingMode.HALF_UP)
                        .doubleValue();

        return new FixedSummaryDto(
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
    }

    private FixedProgressDto buildProgress(
            List<FixedPaymentOccurrence> allInPeriod,
            List<FixedPaymentOccurrence> paidInPeriod,
            List<FixedPaymentOccurrence> upcomingInPeriod,
            int activeFixedCount
    ) {
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

        return new FixedProgressDto(
                paidInPeriod.size(),
                allInPeriod.size(),
                paidPct,
                nextDueDate,
                nextDueName,
                biggestUpcoming != null ? biggestUpcoming.getFixedPayment().getTitle() : null,
                biggestUpcoming != null ? biggestUpcoming.getExpectedAmount() : null,
                activeFixedCount
        );
    }

    private RiskIndicatorDto buildRiskIndicator(FixedSummaryDto summary, BigDecimal currentBalance) {
        BigDecimal unpaidTotal = summary.remainingAmount().add(summary.overdueAmount());
        BigDecimal balanceAfterFixed = currentBalance.subtract(unpaidTotal);
        boolean isAtRisk = balanceAfterFixed.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal shortfallAmount = isAtRisk ? balanceAfterFixed.abs() : null;
        return new RiskIndicatorDto(isAtRisk, shortfallAmount);
    }

    private BigDecimal sumExpectedAmount(List<FixedPaymentOccurrence> occurrences) {
        return occurrences.stream()
                .map(FixedPaymentOccurrence::getExpectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
