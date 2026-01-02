package com.mikeshaggy.backend.repository;

import com.mikeshaggy.backend.domain.transaction.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Integer> {
    
    List<Wallet> findByUserId(Integer userId);

    @Query("SELECT w FROM Wallet w JOIN FETCH w.user WHERE w.id = :id")
    Optional<Wallet> findByIdWithUser(@Param("id") Integer id);
    
    boolean existsByUserIdAndName(Integer userId, String name);
}
