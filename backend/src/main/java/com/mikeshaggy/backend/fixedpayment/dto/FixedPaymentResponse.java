package com.mikeshaggy.backend.fixedpayment.dto;

import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import com.mikeshaggy.backend.fixedpayment.enums.Cycle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FixedPaymentResponse(
        Integer id,
        Integer walletId,
        String walletName,
        Integer categoryId,
        String categoryName,
        String categoryEmoji,
        String title,
        BigDecimal amount,
        LocalDate anchorDate,
        Cycle cycle,
        LocalDate activeFrom,
        LocalDate activeTo,
        String notes,
        LocalDateTime createdAt
) {
    public static FixedPaymentResponse from(FixedPayment fp) {
        return new FixedPaymentResponse(
                fp.getId(),
                fp.getWallet().getId(),
                fp.getWallet().getName(),
                fp.getCategory().getId(),
                fp.getCategory().getName(),
                fp.getCategory().getEmoji(),
                fp.getTitle(),
                fp.getAmount(),
                fp.getAnchorDate(),
                fp.getCycle(),
                fp.getActiveFrom(),
                fp.getActiveTo(),
                fp.getNotes(),
                fp.getCreatedAt()
        );
    }
}
