package com.mikeshaggy.backend.wallet.service;

import com.mikeshaggy.backend.user.service.UserService;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.dto.WalletCreateRequest;
import com.mikeshaggy.backend.wallet.dto.WalletResponse;
import com.mikeshaggy.backend.wallet.dto.WalletUpdateRequest;
import com.mikeshaggy.backend.wallet.repo.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserService userService;

    public List<WalletResponse> getWalletsForUser(UUID userId) {
        return walletRepository.findByUserId(userId).stream()
                .map(WalletResponse::from)
                .toList();
    }

    public WalletResponse getWalletByIdForUser(Integer id, UUID userId) {
        Wallet wallet = getWalletOrThrowForUser(id, userId);
        return WalletResponse.from(wallet);
    }

    public Wallet getWalletEntityByIdForUser(Integer id, UUID userId) {
        return getWalletOrThrowForUser(id, userId);
    }

    @Transactional
    public WalletResponse createWallet(WalletCreateRequest request, UUID userId) {
        User user = userService.getUserOrThrow(userId);

        if (walletRepository.existsByUserIdAndName(userId, request.name())) {
            throw new IllegalArgumentException("Wallet with name '" + request.name() + "' already exists");
        }

        Wallet wallet = request.toEntity();
        wallet.setUser(user);

        Wallet savedWallet = walletRepository.save(wallet);
        
        log.info("Created wallet '{}' with id: {} for user: {}",
                savedWallet.getName(), savedWallet.getId(), userId);
        
        return WalletResponse.from(savedWallet);
    }

    @Transactional
    public WalletResponse updateWallet(Integer id, WalletUpdateRequest request, UUID userId) {
        Wallet wallet = getWalletOrThrowForUser(id, userId);

        if (!wallet.getName().equals(request.name()) &&
            walletRepository.existsByUserIdAndName(userId, request.name())) {
            throw new IllegalArgumentException("Wallet with name '" + request.name() + "' already exists");
        }

        request.applyTo(wallet);

        Wallet updatedWallet = walletRepository.save(wallet);
        
        log.info("Updated wallet id: {} to name: '{}', balance: {}",
                id, updatedWallet.getName(), updatedWallet.getBalance());
        
        return WalletResponse.from(updatedWallet);
    }

    @Transactional
    public void deleteWallet(Integer id, UUID userId) {
        Wallet wallet = getWalletOrThrowForUser(id, userId);
        
        log.info("Deleting wallet '{}' (id: {}) for user: {}",
                wallet.getName(), id, userId);
        
        walletRepository.delete(wallet);
    }

    @Transactional
    public WalletResponse updateWalletBalance(Integer id, BigDecimal newBalance, UUID userId) {
        Wallet wallet = getWalletOrThrowForUser(id, userId);
        
        wallet.setBalance(newBalance);
        Wallet updatedWallet = walletRepository.save(wallet);
        return WalletResponse.from(updatedWallet);
    }

    private Wallet getWalletOrThrowForUser(Integer id, UUID userId) {
        return walletRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Wallet not found with id: " + id));
    }
}
