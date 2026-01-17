package com.mikeshaggy.backend.wallet.service;

import com.mikeshaggy.backend.category.domain.Type;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.wallet.repo.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletBalanceService {

    private final WalletRepository walletRepository;

    @Transactional
    public void applyTransaction(Integer walletId, BigDecimal amount, Type type, UUID userId) {
        Wallet wallet = getWalletOrThrowForUser(walletId, userId);
        BigDecimal oldBalance = wallet.getBalance();
        BigDecimal newBalance = calculateBalanceAfterTransaction(oldBalance, amount, type);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);
        
        log.debug("Applied {} transaction of {} to wallet {}: {} -> {}",
                type, amount, walletId, oldBalance, newBalance);
    }

    @Transactional
    public void reverseTransaction(Integer walletId, BigDecimal amount, Type type, UUID userId) {
        Wallet wallet = getWalletOrThrowForUser(walletId, userId);
        BigDecimal oldBalance = wallet.getBalance();
        BigDecimal newBalance = calculateBalanceAfterReversal(oldBalance, amount, type);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);
        
        log.debug("Reversed {} transaction of {} from wallet {}: {} -> {}",
                type, amount, walletId, oldBalance, newBalance);
    }

    private Wallet getWalletOrThrowForUser(Integer id, UUID userId) {
        return walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found with id: " + id));
    }

    private BigDecimal calculateBalanceAfterTransaction(BigDecimal currentBalance, BigDecimal amount, Type type) {
        return type.equals(Type.EXPENSE)
                ? currentBalance.subtract(amount)
                : currentBalance.add(amount);
    }

    private BigDecimal calculateBalanceAfterReversal(BigDecimal currentBalance, BigDecimal amount, Type type) {
        return type.equals(Type.EXPENSE)
                ? currentBalance.add(amount)
                : currentBalance.subtract(amount);
    }
}
