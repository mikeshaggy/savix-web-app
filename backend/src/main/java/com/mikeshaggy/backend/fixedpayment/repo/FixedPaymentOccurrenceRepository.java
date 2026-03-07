package com.mikeshaggy.backend.fixedpayment.repo;

import com.mikeshaggy.backend.fixedpayment.domain.FixedPaymentOccurrence;
import com.mikeshaggy.backend.fixedpayment.domain.OccurrenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FixedPaymentOccurrenceRepository extends JpaRepository<FixedPaymentOccurrence, Long> {

    @Query("SELECT MAX(o.dueDate) FROM FixedPaymentOccurrence o WHERE o.fixedPayment.id = :fixedPaymentId")
    Optional<LocalDate> findMaxDueDateByFixedPaymentId(@Param("fixedPaymentId") Integer fixedPaymentId);

    boolean existsByFixedPaymentIdAndDueDate(Integer fixedPaymentId, LocalDate dueDate);

    @Query("""
        SELECT o FROM FixedPaymentOccurrence o
        WHERE o.fixedPayment.id IN :fixedPaymentIds
        AND o.status = :status
        ORDER BY o.dueDate ASC
    """)
    List<FixedPaymentOccurrence> findByFixedPaymentIdsAndStatus(
            @Param("fixedPaymentIds") List<Integer> fixedPaymentIds,
            @Param("status") OccurrenceStatus status
    );

    @Query("""
        SELECT o FROM FixedPaymentOccurrence o
        WHERE o.fixedPayment.id IN :fixedPaymentIds
        AND o.status = 'PENDING'
        AND o.dueDate < :today
    """)
    List<FixedPaymentOccurrence> findPendingOverdueOccurrences(
            @Param("fixedPaymentIds") List<Integer> fixedPaymentIds,
            @Param("today") LocalDate today
    );

    @Query("""
        SELECT o FROM FixedPaymentOccurrence o
        WHERE o.fixedPayment.id = :fixedPaymentId
        AND o.status = 'PENDING'
        AND o.dueDate > :today
    """)
    List<FixedPaymentOccurrence> findFuturePendingByFixedPaymentId(
            @Param("fixedPaymentId") Integer fixedPaymentId,
            @Param("today") LocalDate today
    );

    @Query("""
        SELECT o FROM FixedPaymentOccurrence o
        WHERE o.fixedPayment.id IN :fixedPaymentIds
        AND o.dueDate BETWEEN :from AND :to
    """)
    List<FixedPaymentOccurrence> findAllByFixedPaymentIdsAndDueDateBetween(
            @Param("fixedPaymentIds") List<Integer> fixedPaymentIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
