package com.mikeshaggy.backend.fixedpayment.domain;

import com.mikeshaggy.backend.category.domain.Category;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fixed_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Cycle cycle;

    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;

    @Column(name = "active_to")
    private LocalDate activeTo;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixedPayment)) return false;
        return id != null && id.equals(((FixedPayment) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
