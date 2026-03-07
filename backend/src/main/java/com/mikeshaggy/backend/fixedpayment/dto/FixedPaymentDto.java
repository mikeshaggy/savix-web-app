package com.mikeshaggy.backend.fixedpayment.dto;

import com.mikeshaggy.backend.fixedpayment.domain.Cycle;
import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FixedPaymentDto(
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
    public static FixedPaymentDto from(FixedPayment fp) {
        return new FixedPaymentDto(
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
