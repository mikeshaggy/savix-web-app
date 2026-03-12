package com.mikeshaggy.backend.fixedpayment.dto;

import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.enums.OccurrenceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record FixedOccurrenceRowDto(
        Long occurrenceId,
        Integer fixedPaymentId,
        String title,
        Integer categoryId,
        String categoryName,
        String categoryEmoji,
        Integer walletId,
        BigDecimal expectedAmount,
        BigDecimal paidAmount,
        LocalDate dueDate,
        OccurrenceStatus status,
        long daysDelta,
        LocalDateTime paidAt,
        Long transactionId
) {
    public static FixedOccurrenceRowDto from(FixedPaymentOccurrence o, LocalDate today) {
        var fp = o.getFixedPayment();
        return new FixedOccurrenceRowDto(
                o.getId(),
                fp.getId(),
                fp.getTitle(),
                fp.getCategory().getId(),
                fp.getCategory().getName(),
                fp.getCategory().getEmoji(),
                fp.getWallet().getId(),
                o.getExpectedAmount(),
                o.getPaidAmount(),
                o.getDueDate(),
                o.getStatus(),
                ChronoUnit.DAYS.between(today, o.getDueDate()),
                o.getPaidAt(),
                o.getTransaction() != null ? o.getTransaction().getId() : null
        );
    }
}
