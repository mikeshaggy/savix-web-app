package com.mikeshaggy.backend.fixedpayment.repo;

import com.mikeshaggy.backend.fixedpayment.domain.FixedPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FixedPaymentRepository extends JpaRepository<FixedPayment, Integer> {

    @Query("""
        SELECT fp FROM FixedPayment fp
        JOIN fp.wallet w
        WHERE w.user.id = :userId
        AND (fp.activeTo IS NULL OR fp.activeTo >= :today)
    """)
    List<FixedPayment> findAllActiveByUserId(
            @Param("userId") UUID userId,
            @Param("today") LocalDate today
    );

    @Query("""
        SELECT fp FROM FixedPayment fp
        WHERE fp.wallet.id = :walletId
        AND fp.wallet.user.id = :userId
        AND (fp.activeTo IS NULL OR fp.activeTo >= :today)
    """)
    List<FixedPayment> findAllActiveByWalletIdAndUserId(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId,
            @Param("today") LocalDate today
    );

    @Query("""
        SELECT fp FROM FixedPayment fp
        WHERE fp.wallet.id = :walletId
        AND fp.wallet.user.id = :userId
    """)
    List<FixedPayment> findAllByWalletIdAndUserId(
            @Param("walletId") Integer walletId,
            @Param("userId") UUID userId
    );
}
