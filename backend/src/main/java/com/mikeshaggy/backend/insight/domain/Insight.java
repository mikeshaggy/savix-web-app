package com.mikeshaggy.backend.insight.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A persisted Insights v2 event (Stage 7.1, {@code 06_insights.sql}). At most one row exists per
 * {@code (walletId, type, cycleStart, dedupeKey)} — enforced by the database, not by the application. A dismissed
 * row keeps its key, which is what stops the same event from being re-created later in that cycle.
 * <p>
 * Owner and wallet are plain columns (no lazy associations): every read is already scoped by both, and the
 * foreign keys live in the schema. {@code payload} is raw JSON text stored as {@code jsonb}.
 */
@Entity
@Table(name = "insights",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_insights_wallet_type_cycle_key",
                columnNames = {"wallet_id", "type", "cycle_start", "dedupe_key"}),
        indexes = {
                @Index(name = "idx_insights_wallet_cycle", columnList = "wallet_id, cycle_start"),
                @Index(name = "idx_insights_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Insight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "wallet_id", nullable = false)
    private Integer walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InsightType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InsightSeverity severity;

    @Column(name = "cycle_start", nullable = false)
    private LocalDate cycleStart;

    @Column(name = "dedupe_key", nullable = false, length = 120)
    private String dedupeKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    // SqlTypes.JSON is jsonb on PostgreSQL (06_insights.sql) and json on H2
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 40)
    private InsightActionType actionType;

    @Column(name = "action_ref", length = 120)
    private String actionRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    public boolean isDismissed() {
        return dismissedAt != null;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Insight)) return false;
        return id != null && id.equals(((Insight) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
