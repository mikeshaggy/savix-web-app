package com.mikeshaggy.backend.wallet.repo;

import com.mikeshaggy.backend.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, Integer> {
    
    List<Wallet> findByUserId(UUID userId);
    
    Optional<Wallet> findByIdAndUserId(Integer id, UUID userId);
    
    boolean existsByUserIdAndName(UUID userId, String name);
}
