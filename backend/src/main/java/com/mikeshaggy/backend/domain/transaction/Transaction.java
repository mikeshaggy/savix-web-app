package com.mikeshaggy.backend.domain.transaction;

import com.mikeshaggy.backend.domain.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Builder
    public Transaction(Long id, User user, Category category, String title, BigDecimal amount,
                       LocalDate transactionDate, String notes, Importance importance,
                       Cycle cycle, Type type, LocalDateTime createdAt) {
        this.id = id;
        this.user = user;
        this.category = category;
        this.title = title;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.notes = notes;
        this.importance = importance;
        this.cycle = cycle;
        this.type = type;
        this.createdAt = createdAt;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate transactionDate;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Importance importance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Cycle cycle;

    @Enumerated(EnumType.STRING)
    private Type type;

    @PrePersist
    @PreUpdate
    private void validateImportanceForType() {
        if (type == Type.INCOME && importance != null) {
            throw new IllegalStateException("Income transactions cannot have importance");
        }
        if (type == Type.EXPENSE && importance == null) {
            throw new IllegalStateException("Expense transactions must have importance");
        }
    }

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction)) return false;
        return id != null && id.equals(((Transaction) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
