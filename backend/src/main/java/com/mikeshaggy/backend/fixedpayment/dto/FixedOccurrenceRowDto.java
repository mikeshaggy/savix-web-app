package com.mikeshaggy.backend.fixedpayment.dto;

import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * One occurrence row as shown on the dashboard tile and the Fixed Payments page.
 *
 * <p>Paid semantics (Stage 3.2): {@code paidDate} is the date of the linked
 * transaction, {@code lateDays} = {@code paidDate − dueDate} and
 * {@code paidOnTime} = {@code lateDays <= 0}. All three are {@code null} for
 * unpaid rows. {@code paidAmount} is the actual transaction amount;
 * {@code expectedAmount} stays the planned figure.
 */
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
        Long transactionId,
        LocalDate paidDate,
        Integer lateDays,
        Boolean paidOnTime
) {
    public static FixedOccurrenceRowDto from(FixedPaymentOccurrence o, LocalDate today) {
        var fp = o.getFixedPayment();
        LocalDate paidDate = paidDateOf(o);
        Integer lateDays = paidDate == null
                ? null
                : (int) ChronoUnit.DAYS.between(o.getDueDate(), paidDate);
        Boolean paidOnTime = lateDays == null ? null : lateDays <= 0;
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
                o.getTransaction() != null ? o.getTransaction().getId() : null,
                paidDate,
                lateDays,
                paidOnTime
        );
    }

    /**
     * The linked transaction's date wins; {@code paidAt} is only a fallback for
     * legacy rows that were marked paid without a transaction (their
     * {@code paidAt} is the link timestamp, so the date part is the best
     * available approximation).
     */
    public static LocalDate paidDateOf(FixedPaymentOccurrence o) {
        if (o.getStatus() != OccurrenceStatus.PAID) {
            return null;
        }
        if (o.getTransaction() != null) {
            return o.getTransaction().getTransactionDate();
        }
        return o.getPaidAt() != null ? o.getPaidAt().toLocalDate() : null;
    }
}
