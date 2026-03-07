package com.mikeshaggy.backend.transfer.domain;

import com.mikeshaggy.backend.wallet.domain.Wallet;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id", nullable = false)
    private Wallet fromWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_wallet_id", nullable = false)
    private Wallet toWallet;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate transferDate;

    private String notes;

    @CreationTimestamp
    private Instant createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transfer)) return false;
        return id != null && id.equals(((Transfer) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
