package com.mikeshaggy.backend.insight.repository;

import com.mikeshaggy.backend.insight.domain.Insight;
import com.mikeshaggy.backend.insight.domain.InsightActionType;
import com.mikeshaggy.backend.insight.domain.InsightSeverity;
import com.mikeshaggy.backend.insight.domain.InsightType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every query is scoped by the owning user as well as the wallet — an insight id alone never reaches a row. */
public interface InsightRepository extends JpaRepository<Insight, Long> {

    /** Non-dismissed insights of one wallet's cycle, oldest first (served by {@code idx_insights_wallet_cycle}). */
    @Query("""
            SELECT i FROM Insight i
            WHERE i.userId = :userId
              AND i.walletId = :walletId
              AND i.cycleStart = :cycleStart
              AND i.dismissedAt IS NULL
            ORDER BY i.id ASC
            """)
    List<Insight> findActiveByWalletAndCycle(@Param("userId") UUID userId,
                                             @Param("walletId") Integer walletId,
                                             @Param("cycleStart") LocalDate cycleStart);

    /** Whether the event already exists for the cycle — dismissed or not (a dismissed event is never re-created). */
    @Query("""
            SELECT COUNT(i) > 0 FROM Insight i
            WHERE i.walletId = :walletId
              AND i.type = :type
              AND i.cycleStart = :cycleStart
              AND i.dedupeKey = :dedupeKey
            """)
    boolean existsByWalletTypeCycleAndKey(@Param("walletId") Integer walletId,
                                          @Param("type") InsightType type,
                                          @Param("cycleStart") LocalDate cycleStart,
                                          @Param("dedupeKey") String dedupeKey);

    /** Whether the key exists for the wallet in any cycle, dismissed or not ({@code Recurrence.ONCE_PER_WALLET}). */
    @Query("""
            SELECT COUNT(i) > 0 FROM Insight i
            WHERE i.userId = :userId
              AND i.walletId = :walletId
              AND i.type = :type
              AND i.dedupeKey = :dedupeKey
            """)
    boolean existsByWalletTypeAndKey(@Param("userId") UUID userId,
                                     @Param("walletId") Integer walletId,
                                     @Param("type") InsightType type,
                                     @Param("dedupeKey") String dedupeKey);

    /** Whether the key was dismissed for the wallet in any cycle ({@code Recurrence.UNTIL_DISMISSED}). */
    @Query("""
            SELECT COUNT(i) > 0 FROM Insight i
            WHERE i.userId = :userId
              AND i.walletId = :walletId
              AND i.type = :type
              AND i.dedupeKey = :dedupeKey
              AND i.dismissedAt IS NOT NULL
            """)
    boolean existsDismissedByWalletTypeAndKey(@Param("userId") UUID userId,
                                              @Param("walletId") Integer walletId,
                                              @Param("type") InsightType type,
                                              @Param("dedupeKey") String dedupeKey);

    /** The non-dismissed row of one event in one cycle, if any (Stage 7.3 refresh of a condition insight). */
    @Query("""
            SELECT i FROM Insight i
            WHERE i.userId = :userId
              AND i.walletId = :walletId
              AND i.type = :type
              AND i.cycleStart = :cycleStart
              AND i.dedupeKey = :dedupeKey
              AND i.dismissedAt IS NULL
            """)
    Optional<Insight> findActiveByWalletTypeCycleAndKey(@Param("userId") UUID userId,
                                                        @Param("walletId") Integer walletId,
                                                        @Param("type") InsightType type,
                                                        @Param("cycleStart") LocalDate cycleStart,
                                                        @Param("dedupeKey") String dedupeKey);

    /**
     * Rewrites the content of a non-dismissed insight in place. Identity ({@code id}, wallet, type, cycle, dedupe key),
     * {@code created_at} and {@code dismissed_at} are never touched; a row dismissed in the meantime updates nothing.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Insight i
            SET i.severity = :severity, i.title = :title, i.description = :description, i.payload = :payload,
                i.actionType = :actionType, i.actionRef = :actionRef
            WHERE i.id = :id AND i.userId = :userId AND i.dismissedAt IS NULL
            """)
    int refreshContent(@Param("id") Long id,
                       @Param("userId") UUID userId,
                       @Param("severity") InsightSeverity severity,
                       @Param("title") String title,
                       @Param("description") String description,
                       @Param("payload") String payload,
                       @Param("actionType") InsightActionType actionType,
                       @Param("actionRef") String actionRef);

    Optional<Insight> findByIdAndUserId(Long id, UUID userId);

    /** Sets {@code dismissed_at} once; a second call (or another user's id) updates nothing. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Insight i SET i.dismissedAt = :dismissedAt
            WHERE i.id = :id AND i.userId = :userId AND i.dismissedAt IS NULL
            """)
    int dismiss(@Param("id") Long id, @Param("userId") UUID userId, @Param("dismissedAt") Instant dismissedAt);
}
