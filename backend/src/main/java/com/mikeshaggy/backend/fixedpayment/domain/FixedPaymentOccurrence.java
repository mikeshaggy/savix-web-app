package com.mikeshaggy.backend.fixedpayment.domain;

import com.mikeshaggy.backend.fixedpayment.enums.OccurrenceStatus;
import com.mikeshaggy.backend.transaction.domain.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fixed_payment_occurrences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fixed_payment_id", "due_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedPaymentOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixed_payment_id", nullable = false)
    private FixedPayment fixedPayment;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "expected_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "paid_amount", precision = 12, scale = 2)
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OccurrenceStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixedPaymentOccurrence)) return false;
        return id != null && id.equals(((FixedPaymentOccurrence) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
