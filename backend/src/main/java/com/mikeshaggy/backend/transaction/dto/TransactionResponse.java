package com.mikeshaggy.backend.transaction.dto;

import com.mikeshaggy.backend.category.domain.Type;
import com.mikeshaggy.backend.transaction.domain.Cycle;
import com.mikeshaggy.backend.transaction.domain.Importance;
import com.mikeshaggy.backend.transaction.domain.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        Integer walletId,
        String walletName,
        Integer categoryId,
        String categoryName,
        Type categoryType,
        String categoryEmoji,
        String title,
        BigDecimal amount,
        LocalDate transactionDate,
        String notes,
        Importance importance,
        Cycle cycle,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getWallet().getId(),
                transaction.getWallet().getName(),
                transaction.getCategory().getId(),
                transaction.getCategory().getName(),
                transaction.getCategory().getType(),
                transaction.getCategory().getEmoji(),
                transaction.getTitle(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getNotes(),
                transaction.getImportance(),
                transaction.getCycle(),
                transaction.getCreatedAt()
        );
    }
}
