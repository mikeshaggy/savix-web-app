package com.mikeshaggy.backend.dto;

import com.mikeshaggy.backend.domain.transaction.Cycle;
import com.mikeshaggy.backend.domain.transaction.Importance;
import com.mikeshaggy.backend.domain.transaction.Type;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDTO {

    private Long id;

    @NotNull(message = "User ID is required")
    private Integer userId;

    @NotNull(message = "Category ID is required")
    private Integer categoryId;

    // todo: Consider removing this field if not needed in the DTO
    private String categoryName;

    @NotBlank(message = "Title is required")
    @Size(max = 50, message = "Title must not exceed 50 characters")
    private String title;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Transaction date is required")
    private LocalDate transactionDate;

    private String notes;

    @NotNull(message = "Importance is required")
    private Importance importance;

    @NotNull(message = "Cycle is required")
    private Cycle cycle;

    @NotNull(message = "Type is required")
    private Type type;

    @AssertTrue(message = "Importance can only be set for expenses")
    public boolean isImportanceValidForType() {
        if (type == Type.EXPENSE) return importance == null;
        return importance != null;
    }

    private LocalDateTime createdAt;
}
