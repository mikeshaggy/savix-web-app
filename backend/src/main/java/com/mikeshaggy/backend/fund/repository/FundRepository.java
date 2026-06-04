package com.mikeshaggy.backend.fund.repository;

import com.mikeshaggy.backend.fund.domain.Fund;
import com.mikeshaggy.backend.fund.domain.FundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundRepository extends JpaRepository<Fund, Long> {

    @Query("SELECT f FROM Fund f JOIN FETCH f.fundWallet WHERE f.user.id = :userId ORDER BY f.createdAt DESC")
    List<Fund> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT f FROM Fund f JOIN FETCH f.fundWallet WHERE f.user.id = :userId AND f.status = :status ORDER BY f.createdAt DESC")
    List<Fund> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") FundStatus status);

    @Query("SELECT f FROM Fund f JOIN FETCH f.fundWallet WHERE f.id = :id AND f.user.id = :userId")
    Optional<Fund> findByIdAndUserId(@Param("id") Long id, @Param("userId") UUID userId);

    boolean existsByUserIdAndNameIgnoreCaseAndStatus(UUID userId, String name, FundStatus status);
}
