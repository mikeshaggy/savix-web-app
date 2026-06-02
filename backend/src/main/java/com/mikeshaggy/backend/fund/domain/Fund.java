package com.mikeshaggy.backend.fund.domain;

import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "funds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_wallet_id", nullable = false)
    private Wallet fundWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_wallet_id")
    private Wallet sourceWallet;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "PLN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FundStatus status = FundStatus.ACTIVE;

    @Column(length = 10)
    private String icon;

    @Column(length = 20)
    private String color;

    private LocalDate deadlineDate;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fund)) return false;
        return id != null && id.equals(((Fund) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
